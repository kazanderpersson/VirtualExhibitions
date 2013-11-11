import jade.util.leap.Serializable;


public class Artifact implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3089947323146489985L;
	
	private int id;
	private String name;
	private String creator;
	private String creationDate;
	private String creationPlace;
	private String genre;
	
	public Artifact(int id, String name, String creator, String creationDate, String creationPlace, String genre) {
		this.id = id;
		this.name = name;
		this.creator = creator;
		this.creationDate = creationDate;
		this.creationPlace = creationPlace;
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

	public String getCreationPlace() {
		return creationPlace;
	}

	public String getGenre() {
		return genre;
	}
	
	
}
