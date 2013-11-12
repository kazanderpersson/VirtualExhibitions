import jade.util.leap.Serializable;

import java.util.ArrayList;


public class Artifact implements Serializable {
	private static final long serialVersionUID = 3089947323146489985L;
	
	private int id;
	private String name;
	private String creator;
	private String creationDate;
	private String type;
	private String description;
	private ArrayList<String> genres;
	
	public Artifact(int id, String name, String creator, String creationDate, String type, String description, ArrayList<String> genres) {
		this.id = id;
		this.name = name;
		this.creator = creator;
		this.creationDate = creationDate;
		this.type = type;
		this.description = description;
		this.genres = genres;
	}

	public int getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getCreator() {
		return creator;
	}

	public String getCreationDate() {
		return creationDate;
	}

	public String getType() {
		return type;
	}
	
	public String getDescription() {
		return description;
	}

	public ArrayList<String> getGenre() {
		return genres;
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof Artifact)
			return id == ((Artifact)obj).getId();
		else
			return false;
	}
}
