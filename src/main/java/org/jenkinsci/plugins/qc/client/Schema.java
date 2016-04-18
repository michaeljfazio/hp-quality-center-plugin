package org.jenkinsci.plugins.qc.client;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

class Schema {

	@XmlRootElement(name = "Domain")
	public static class Domain {

		@XmlAttribute(name = "Name")
		public String name;

	}

	@XmlRootElement(name = "Project")
	public static class Project {

		@XmlAttribute(name = "Name")
		public String name;

	}

	@XmlRootElement(name = "Entity")
	public static class Entity {

		@XmlAttribute(name = "Type")
		public String type;

		@XmlElement(name = "Field")
		@XmlElementWrapper(name = "Fields")
		public List<Field> fields = new ArrayList<Schema.Field>();

		public Field field(String name) {
			for (Field field : fields) {
				if (field.name.equals(name)) {
					return field;
				}
			}
			throw new IllegalArgumentException("No such entity field with name = " + name);
		}

		public void add(String name, String value) {
			Field field = new Field();
			field.name = name;
			field.value = value;
			fields.add(field);
		}

	}

	@XmlRootElement(name = "Field")
	public static class Field {

		@XmlAttribute(name = "Name")
		public String name;

		@XmlElement(name = "Value")
		public String value;

	}

	@XmlRootElement(name = "QCRestException")
	public static class QCRestException {

		@XmlElement(name = "Id")
		public String id;

		@XmlElement(name = "Title")
		public String title;

		@XmlElement(name = "StackTrace")
		public String stacktrace;

	}

}
