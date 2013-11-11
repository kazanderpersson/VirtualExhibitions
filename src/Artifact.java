import jade.util.leap.Serializable;


public class Artifact implements Serializable {
	private static final long serialVersionUID = 3089947323146489985L;
	
	private int id;
	private String name;
	private String creator;
	private String creationDate;
	private String type;
	private String genre;
	
	public Artifact(int id, String name, String creator, String creationDate, String type, String genre) {
		this.id = id;
		this.name = name;
		this.creator = creator;
		this.creationDate = creationDate;
		this.type = type;
		this.genre = genre;
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

	public String getGenre() {
		return genre;
	}
	
	
}
