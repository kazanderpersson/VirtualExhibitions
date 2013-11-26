import jade.util.leap.Serializable;

import java.util.Calendar;
import java.util.ArrayList;

/**
 * 
 *	This class wraps information about a person (user).
 *
 */
public class Profile implements Serializable{
	private static final long serialVersionUID = 1L;
	
	private int id;
	private String name;
	private String gender;
	private int yearBorned;
	private String occupation;
	private ArrayList<String> interests;
	private ArrayList<Integer> visitedItemsID;
	
	public Profile(int id, String name, String gender, int yearBorned, String occupation, ArrayList<String> interests) {
		this.id = id;
		this.name = name;
		this.gender = gender;
		this.yearBorned = yearBorned;
		this.occupation = occupation;
		this.interests = interests;
		visitedItemsID = new ArrayList<Integer>();
	}

	public int getId() {
		return id;
	}
	
	public String getName() {
		return name;
	}
	
	public String getGender() {
		return gender;
	}
	
	public int getAge() {
		return Calendar.getInstance().get(Calendar.YEAR) - yearBorned;
	}
	
	public String getOccupation() {
		return occupation;
	}
	
	public ArrayList<String> getInterests() {
		return interests;
	}
	
	public ArrayList<Integer> getVisitedItemsID() {
		return visitedItemsID;
	}
	
	public void setVisitedItem(int itemID) {
		if (!visitedItemsID.contains(itemID))
			visitedItemsID.add(itemID);
	}
}