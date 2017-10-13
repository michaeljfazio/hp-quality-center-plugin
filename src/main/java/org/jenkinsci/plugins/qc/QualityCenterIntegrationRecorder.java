package org.jenkinsci.plugins.qc;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.tasks.junit.*;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.validator.routines.UrlValidator;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.qc.client.Entity;
import org.jenkinsci.plugins.qc.client.QualityCenter;
import org.jenkinsci.plugins.qc.client.Query;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This {@link Recorder} provides a one-way synchroniztion of Maven unit test
 * results to HP ALM Quality Center.
 *
 * @author Michael Fazio
 */
public class QualityCenterIntegrationRecorder extends Recorder implements SimpleBuildStep {

    private static final UrlValidator URL_VALIDATOR = new UrlValidator(new String[]{"http", "https"},
            UrlValidator.ALLOW_LOCAL_URLS);

    private final String domain;
    private final String project;
    private final String planFolder;
    private final String labFolder;
    private String userDefinedFields;
    private boolean failOnNoTestResults = true;

    /**
     * Constructor
     *
     * @param domain     The QC domain.
     * @param project    The QC project.
     * @param planFolder The QC plan folder that test plans shall be created in.
     * @param labFolder  The QC lab folder that new test sets shall be created in.
     */
    @DataBoundConstructor
    public QualityCenterIntegrationRecorder(String domain, String project, String planFolder, String labFolder) {
        this.domain = domain;
        this.project = project;
        this.planFolder = planFolder;
        this.labFolder = labFolder;
    }

    /**
     * {@inheritDoc}
     */
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> build, @Nonnull FilePath filePath, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        PrintStream logger = listener.getLogger();

        QualityCenterIntegrationDescriptor descriptor = getDescriptor();
        QualityCenter qc = QualityCenter.create(descriptor.url);

        logger.println("Synchronizing test results with ALM instance: " + descriptor.url);

        TestResultAction report = build.getAction(TestResultAction.class);

        if (report == null) {
            if (failOnNoTestResults) {
                throw new AbortException("No test results found. Results will not be published to Quality Center.");
            }

            return;
        }

        // Authenticate Quality Center Session
        if (!qc.login(descriptor.username, descriptor.password)) {
            throw new AbortException("Authentication failed!");
        }

        final Query query = qc.query(domain, project);
        Entity planFolderEntity = resolveEntityPath(query.resource("test-folders"), planFolder.split("/"));
        Entity labFolderEntity = resolveEntityPath(query.resource("test-set-folders"), labFolder.split("/"));

        if (planFolderEntity == null) {
            throw new AbortException("Plan folder '" + planFolder + "' does not exists.");
        }

        if (labFolderEntity == null) {
            throw new AbortException("Lab folder '" + labFolder + "' does not exists.");
        }

        // Map out all the tests by name
        Map<String, Entity> tests = new HashMap<String, Entity>();
        Map<String, Entity> testsById = new HashMap<String, Entity>();
        for (Entity e : qc.query(domain, project).resource("tests")
                .filter("parent-id[={0}]", planFolderEntity.get("id")).execute()) {
            tests.put(e.get("name"), e);
            testsById.put(e.get("id"), e);
        }

        TestResult result = report.getResult();
        for (PackageResult packageResult : result.getChildren()) {
            for (ClassResult classResult : packageResult.getChildren()) {
                if (!tests.containsKey(classResult.getFullName())) {
                    logger.println("Creating test: " + classResult.getFullName());
                    Entity e = qc.create(domain, project, "tests");
                    e.setType("test");
                    e.add("name", classResult.getFullName());
                    e.add("parent-id", planFolderEntity.get("id"));
                    e.add("owner", descriptor.username);
                    e.add("subtype-id", "VAPI-XP-TEST");

                    if (userDefinedFields != null) {
                        Matcher matcher = Pattern.compile("([^=]+)=([^=]+)(?:,|$)").matcher(userDefinedFields);
                        while (matcher.find()) {
                            e.add(matcher.group(1), matcher.group(2));
                        }
                    }

                    e.add("status", "Ready");
                    e.post();
                    tests.put(e.get("name"), e);
                } else {
                    logger.println("Test exists: " + classResult.getFullName());
                }
            }
        }

        // Check if test set exists (create it if it is missing)
        String jobName = build.getDisplayName();
        List<Entity> sets = qc.query(domain, project).resource("test-sets")
                .filter("parent-id[={0}];name[\"{1}\"]", labFolderEntity.get("id"), jobName).execute();
        Entity set = sets.isEmpty() ? null : sets.get(0);
        if (set == null) {
            logger.println("Creating test set: " + jobName);
            Entity e = qc.create(domain, project, "test-sets");
            e.setType("test-set");
            e.add("subtype-id", "hp.qc.test-set.default");
            e.add("parent-id", labFolderEntity.get("id"));
            e.add("name", jobName);
            e.post();
            set = e;
        } else {
            logger.println("Test set exists: " + jobName);
        }

        // Map out all the test instances by name
        Map<String, Entity> instances = new HashMap<String, Entity>();
        for (Entity e : qc.query(domain, project).resource("test-instances").filter("cycle-id[{0}]", set.get("id"))
                .execute()) {
            Entity test = testsById.get(e.get("test-id"));
            instances.put(test.get("name"), e);
        }

        // Check if a test instance already exists (create if it is missing)
        result = report.getResult();
        for (PackageResult packageResult : result.getChildren()) {
            for (ClassResult classResult : packageResult.getChildren()) {
                if (!instances.containsKey(classResult.getFullName())) {
                    logger.println("Creating test instance: " + classResult.getFullName());
                    Entity e = qc.create(domain, project, "test-instances");
                    e.setType("test-instance");
                    e.add("subtype-id", "hp.qc.test-instance.VAPI-XP-TEST");
                    e.add("test-id", tests.get(classResult.getFullName()).get("id"));
                    e.add("test-config-id", tests.get(classResult.getFullName()).get("id"));
                    e.add("cycle-id", set.get("id"));
                    e.add("test-order", "0");
                    e.post();

                    instances.put(classResult.getFullName(), e);
                } else {
                    logger.println("Test instance exists: " + classResult.getFullName());
                }
            }
        }

        result = report.getResult();
        for (PackageResult packageResult : result.getChildren()) {
            for (ClassResult classResult : packageResult.getChildren()) {
                String status = classResult.isPassed() ? "Passed" : "Failed";
                Computer computer = Computer.currentComputer();
                logger.println("Adding test run: " + classResult.getFullName() + " (" + status + ")");
                Entity i = instances.get(classResult.getFullName());
                Entity r = qc.create(domain, project, "runs");
                r.setType("run");
                r.add("subtype-id", "hp.qc.run.VAPI-XP-TEST");
                r.add("owner", descriptor.username);
                r.add("state", "Finished");
                r.add("cycle-id", set.get("id"));
                r.add("testcycl-id", i.get("id"));
                r.add("test-id", i.get("test-id"));
                r.add("duration", Integer.toString(Math.round(classResult.getDuration())));
                r.add("name", build.getDisplayName());
                r.add("host", computer != null ? computer.getHostName() : "");
                r.add("status", "Not Completed"); // Must be set 'Not
                // Completed' initially
                r.post();

                // Updated the run to pass/fail (will trigger test instance
                // to be updated also)
                r.set("status", status);
                r.put();

                // Add a run step for each test case
                for (CaseResult caseResult : classResult.getChildren()) {
                    Entity step = qc.create(domain, project, "runs/" + r.get("id") + "/run-steps");
                    String stepStatus = caseResult.isPassed() ? "Passed" : "Failed";
                    step.setType("run-step");
                    step.add("parent-id", r.get("id"));
                    step.add("name", caseResult.getName());
                    step.add("status", stepStatus);
                    // Only add actual result on failure
                    if (!caseResult.isPassed()) {
                        StringBuilder actual = new StringBuilder();
                        if (caseResult.getStdout() != null) {
                            actual.append(caseResult.getStdout()).append("\n");
                        }
                        if (caseResult.getStderr() != null) {
                            actual.append(caseResult.getStderr()).append("\n");
                        }
                        if (caseResult.getErrorDetails() != null) {
                            actual.append(caseResult.getErrorDetails()).append("\n");
                        }
                        if (caseResult.getErrorStackTrace() != null) {
                            actual.append(caseResult.getErrorStackTrace());
                        }
                        step.add("actual", actual.toString());
                    }
                    logger.println("Adding test run step: " + caseResult.getName() + " (" + stepStatus + ")");
                    step.post();
                }

            }
        }

    }

    private static Entity resolveEntityPath(Query query, String... path) {
        int parentId = 0;
        Entity entity = null;
        for (String next : Arrays.asList(path)) {
            List<Entity> result = query.filter("parent-id[={0}];name[\"{1}\"]", Long.toString(parentId), next).execute();
            if (result.isEmpty()) {
                entity = null;
                break;
            }
            entity = result.get(0);
            parentId = Integer.parseInt(entity.get("id"));
        }
        return entity;
    }

    public String getDomain() {
        return domain;
    }

    public String getProject() {
        return project;
    }

    public String getLabFolder() {
        return labFolder;
    }

    public String getPlanFolder() {
        return planFolder;
    }

    public boolean isFailOnNoTestResults() {
        return failOnNoTestResults;
    }

    /**
     * Setter for optional fail on no test result flag.
     *
     * @param failOnNoTestResults If {@code true} then builds will be marked as failed when no
     *                            test results have been found.
     */
    @DataBoundSetter
    public void setFailOnNoTestResults(boolean failOnNoTestResults) {
        this.failOnNoTestResults = failOnNoTestResults;
    }

    /**
     * Setter for optional user defined fields.
     *
     * @param userDefinedFields Any additional user defined fields and values that will be
     *                          populated when creating new QC test plans.
     */
    @DataBoundSetter
    public void setUserDefinedFields(String userDefinedFields) {
        this.userDefinedFields = userDefinedFields;
    }

    public String getUserDefinedFields() {
        return userDefinedFields;
    }

    @Override
    public QualityCenterIntegrationDescriptor getDescriptor() {
        return (QualityCenterIntegrationDescriptor) super.getDescriptor();
    }

    @Symbol("qc")
    @Extension
    public static final class QualityCenterIntegrationDescriptor extends BuildStepDescriptor<Publisher> {

        private String url;
        private String username;
        private String password;

        public QualityCenterIntegrationDescriptor() {
            super(QualityCenterIntegrationRecorder.class);
            load();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @SuppressWarnings("rawtypes")
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws hudson.model.Descriptor.FormException {
            url = json.getString("url");
            username = json.getString("username");
            password = json.getString("password");
            save();
            return super.configure(req, json);
        }

        public FormValidation doTestConnection(@QueryParameter("url") final String url,
                                               @QueryParameter("username") final String username, @QueryParameter("password") final String password)
                throws IOException, ServletException {

            // We always want a fresh authentication request.
            if (QualityCenter.create(url).login(username, password)) {
                return FormValidation.ok("Authenticated with server successfully.");
            }

            return FormValidation.error("Failed to authenticate with server.");
        }

        public FormValidation doCheckUrl(@QueryParameter String value) throws IOException, ServletException {
            if (!URL_VALIDATOR.isValid(value))
                return FormValidation.error("Please enter a valid URL.");
            return FormValidation.ok();

        }

        public FormValidation doCheckUsername(@QueryParameter final String value) {
            if (value.length() == 0)
                return FormValidation.error("Please enter an account username.");
            return FormValidation.ok();
        }

        public FormValidation doCheckPassword(@QueryParameter final String value) {
            if (value.length() == 0)
                return FormValidation.error("Please enter an account password.");
            return FormValidation.ok();
        }

        public FormValidation doCheckPlanFolder(@QueryParameter("planFolder") final String folder,
                                                @QueryParameter("domain") final String domain, @QueryParameter("project") final String project) {

            if (folder.length() == 0) {
                return FormValidation.error("Please enter a plan folder path.");
            }

            QualityCenter qc = QualityCenter.create(url);
            qc.login(username, password);
            Entity e = resolveEntityPath(qc.query(domain, project).resource("test-folders"), folder.split("/"));
            qc.logout();
            if (null == e) {
                return FormValidation.error("The specified plan folder does not exist.");
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckLabFolder(@QueryParameter("labFolder") final String folder,
                                               @QueryParameter("domain") final String domain, @QueryParameter("project") final String project) {

            if (folder.length() == 0) {
                return FormValidation.error("Please enter a lab folder path.");
            }

            QualityCenter qc = QualityCenter.create(url);
            qc.login(username, password);
            Entity e = resolveEntityPath(qc.query(domain, project).resource("test-set-folders"), folder.split("/"));
            qc.logout();
            if (null == e) {
                return FormValidation.error("The specified lab folder does not exist.");
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckUserDefinedFields(
                @QueryParameter("userDefinedFields") final String userDefinedFields) {

            if (userDefinedFields.length() == 0) {
                return FormValidation.ok();
            }

            if (Pattern.compile("(([^=]+)=([^=]+)(?:,|$))+").matcher(userDefinedFields).matches()) {
                return FormValidation.ok();
            }

            return FormValidation.error("Must be a key-value list separated by commas (e.g. key1=value1,key2=value2)");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "HP Quality Center Integration";
        }

        /**
         * @return the Quality Center server URL.
         */
        public String getUrl() {
            return url;
        }

        /**
         * @return the Quality Center account username.
         */
        public String getUsername() {
            return username;
        }

        /**
         * @return the Quality Center account password.
         */
        public String getPassword() {
            return password;
        }

        public ListBoxModel doFillDomainItems() {
            QualityCenter qc = QualityCenter.create(url);
            qc.login(username, password);

            ListBoxModel model = new ListBoxModel();
            for (String d : qc.domains()) {
                model.add(d, d);
            }

            qc.logout();

            return model;
        }

        public ListBoxModel doFillProjectItems(@QueryParameter("domain") String domain) {
            QualityCenter qc = QualityCenter.create(url);
            qc.login(username, password);

            ListBoxModel model = new ListBoxModel();
            if (domain.length() != 0) {
                for (String d : qc.domains()) {
                    if (d.equals(domain)) {
                        for (String p : qc.projects(d)) {
                            model.add(p, p);
                        }
                    }
                }
            }

            qc.logout();
            return model;
        }

    }

}
