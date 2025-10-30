package de.signaliduna.elpa.hint.util;

public enum ContainerImageNames {
	POSTGRES("hub.docker.system.local/postgres:16-alpine");

	private final String imageName;

	ContainerImageNames(String imageName) {
		this.imageName = imageName;
	}

	public String getImageName() {
		return imageName;
	}
}
