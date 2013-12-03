import jade.util.leap.Serializable;

import java.util.ArrayList;


/**
 *
 *	This is a wrapper for all the artifacts that can be stored in a museum, and maintained by a Curator.
 */
public class Artifact implements Serializable {
	private static final long serialVersionUID = 2L;
	
	private int id;
	private String name;
	private String creator;
	private String creationDate;
	private String type;
	private String description;
	private ArrayList<String> genres;
	private int price;
	
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
	
	public void setPrice(int price) {
		this.price = price;
	}
	
	public int getPrice() {
		return price;
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof Artifact)
			return id == ((Artifact)obj).getId();
		else
			return false;
	}
	
	@Override
	public String toString() {
		return getName();
	}
}
