import jade.util.leap.Serializable;

import java.util.Calendar;
import java.util.ArrayList;

public class Profile implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = -9013238777426582211L;
	
	private int id;
	private String name;
	private boolean genderIsMan;
	private int yearBorned;
	private String occupation;
	private ArrayList<String> interests;
	private ArrayList<Integer> visitedItems;
	
	public Profile(int id, String name, boolean genderIsMan, int yearBorned, String occupation, ArrayList<String> interests) {
		this.id = id;
		this.name = name;
		this.genderIsMan = genderIsMan;
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
	
	public boolean getGenderIsMan() {
		return genderIsMan;
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