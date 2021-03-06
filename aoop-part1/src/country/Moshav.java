package country;

import location.Location;

/**
 * 
 * @author Yarden Hovav, Sharon Vazana
 *
 */
public class Moshav extends Settlement {
	/**
	 * constructor
	 * 
	 * @param name     - name input
	 * @param location - location input
	 * @param people   - Person array for the settlement
	 */
	public Moshav(String name, Location location, int population, Map map) {
		super(name, new Location(location), population, map);
	}
	
	/**
	 * return the type of the settlement
	 */
	public String getSettlementType() {
		return "Moshav";
	}
	
	/**
	 * return the new Ramzoe Color of the settlement
	 */
	protected RamzorColor calculateRamzorGrade() { 
		// p - sick percentage , c - current color
		double p = contagiousPercent();
		double c = getRamzorColor().getValue(); 
		double res = 0.3 + 3 * Math.pow((Math.pow(1.2, c) * (p - 0.35)), 5);
		return getRamzorColor().doubleToRamzorColor(res);
	}
}
