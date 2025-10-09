package de.signaliduna.elpa.hint.util;

public enum ContainerImageNames {
	POSTGRES("hub.docker.system.local/postgres:16-alpine"),
	MONGO("hub.docker.system.local/mongo:6");

	private final String imageName;

	ContainerImageNames(String imageName) {
		this.imageName = imageName;
	}

	public String getImageName() {
		return imageName;
	}
}
