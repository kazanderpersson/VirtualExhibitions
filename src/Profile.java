import jade.util.leap.Serializable;

import java.util.Calendar;
import java.util.ArrayList;

public class Profile implements Serializable{
	private static final long serialVersionUID = -9013238777426582211L;
	
	private int id;
	private String name;
	private String gender;
	private int yearBorned;
	private String occupation;
	private ArrayList<String> interests;
	private ArrayList<Integer> visitedItems;
	
	public Profile(int id, String name, String gender, int yearBorned, String occupation, ArrayList<String> interests) {
		this.id = id;
		this.name = name;
		this.gender = gender;
		this.yearBorned = yearBorned;
		this.occupation = occupation;
		this.interests = interests;
		visitedItems = new ArrayList<Integer>();
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
	
	public ArrayList<Integer> getVisitedItem() {
		return visitedItems;
	}
	
	public void setVisitedItem(int item) {
		if (!visitedItems.contains(item))
			visitedItems.add(item);
	}
}