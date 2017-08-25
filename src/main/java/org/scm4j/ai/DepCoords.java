package org.scm4j.ai;

public class DepCoords {

	private final String artifactId;
	private final String commentStr;
	private final String extension;
	private final String groupId;
	private final String classifier;
	private final Version version;

	public String getComment() {
		return commentStr;
	}

	public DepCoords(String coordsString) {
		String str = coordsString;

		// Comment
		{
			Integer pos = coordsString.indexOf("#");
			
			if (pos > 0) {
				commentStr = str.substring(pos);
				str = str.substring(0, pos);
			} else {
				commentStr = "";
			}
		}

		// Extension
		{
			Integer pos = coordsString.indexOf("@");
			if (pos > 0) {
				extension = str.substring(pos);
				str = str.substring(0, pos);
			} else {
				extension = "";
			}
		}

		String[] strs = str.split(":", -1);
		if (strs.length < 2) {
			throw new IllegalArgumentException("wrong mdep coord: " + coordsString);
		}

		groupId = strs[0];
		artifactId = strs[1];

		classifier = strs.length > 3 ? ":" + strs[3] : "";

		version = new Version(strs.length > 2 ? strs[2] : "");
	}

	public Version getVersion() {
		return version;
	}

	@Override
	public String toString() {
		return toString(version.toString());
	}

	public String toString(String versionStr) {
		return getName() + ":" + versionStr + classifier + extension + commentStr;
	}

	public String getName() {
		return groupId + ":" + artifactId;
	}
	
	public String getGroupId() {
		return groupId;
	}
	
	public String getArtifactId() {
		return artifactId;
	}

	public String getExtension() {
		return extension;
	}
	
	public String getClassifier() {
		return classifier;
	}

}
