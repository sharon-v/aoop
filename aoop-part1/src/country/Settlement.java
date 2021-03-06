package country;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

import io.LogFile;
import location.Location;
import location.Point;
import population.Healthy;
import population.Person;
import population.Sick;
import simulation.Clock;
import virus.IVirus;
import virus.VirusFactory;
import virus.VirusManager;

/**
 * 
 * @author Yarden Hovav, Sharon Vazana
 *
 */
public abstract class Settlement implements Runnable{
	/**
	 * 
	 * @param name - name of the Settlement
	 * @param location - Location of the Settlement
	 * @param people - Person array of residents in Settlement
	 */
	public Settlement(String name, Location location, int population, Map map) {
		m_name = name;
		m_location = new Location(location);
		m_healthyPeople = new Person[0];
		m_ramzorColor = RamzorColor.GREEN;	// default
		m_maxPopulation = population;
		m_vaccineDoses = 0;
		m_connectedSettlements = new Settlement[0];
		m_sickPeople = new Sick[0];
		m_numOfDeceased = 0;
		m_map = map;
	}

	@Override
	public void run() {
		while(m_map.getLoadFlag() == true) {
			synchronized(m_map) {
				while(m_map.getPlayFlag() == false) {
					try {
						m_map.wait();
					}catch( InterruptedException e) {e.printStackTrace();}
				}
			}

			simulation();
			sickToConvalescent();
			transfer();
			vaccineTime();
			attemptedMurder();
			if (isDeceasedOnePercent())
				saveToLogFile();
			m_map.cyclicAwait();
		}
		return;
	}

	/**
	 * transfer person to a random connected settlement
	 */
	private void transfer(){
		Settlement s = randomConnection();
		if (s == null)
			return;
		Person[] persons = randomTransfer(s); 
		for(int i = 0 ; i < persons.length ; ++i)
			transferPerson(persons[i], s);
	}


	@Override
	public String toString() {
		String s1 = getNumOfPeople() + "\nnum of sick: " + m_sickPeople.length + toStringPeople();
		return "settlement name: " + m_name + "\nlocation: " + m_location + "\ncolor grade: " + m_ramzorColor
				+ "\nnum of people: " + s1;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Settlement))
			return false;
		Settlement s = (Settlement)o;
		return m_name.equals(s.m_name) && m_location.equals(s.m_location);
	}

	/**
	 * calculates the Settlements RamzorColor grade
	 * @return new RamzorColor Grade
	 */
	protected abstract RamzorColor calculateRamzorGrade();

	/**
	 * calculates percent of Sick Persons in Settlement
	 * @return percentage of sick people in a certain range
	 */
	public double contagiousPercent() {// 0 to 1 max
		if (getNumOfPeople() == 0)
			return 0;
		return (m_sickPeople.length / (double) getNumOfPeople());
	}// sync this method //

	/**
	 * chooses randomly a Point in Settlement
	 * @return random Point in the settlement
	 */
	public Point randomLocation() {
		int xMax, yMax, xMin, yMin;
		Random ran = new Random();
		xMin = m_location.getPoint().getX();
		yMin = m_location.getPoint().getY();
		xMax = xMin + m_location.getSize().getWidth();
		yMax = yMin + m_location.getSize().getHeith();
		int randX = ran.nextInt(xMax - xMin +1) + xMin;
		int randY = ran.nextInt(yMax - yMin +1) + yMin;
		return new Point(randX, randY);
	}

	/**
	 * adds a new Person to Settlement
	 * @param p - new person to add
	 * @return true if Person added successfully to settlement
	 */
	public synchronized boolean addPerson(Person p) {
		// use equals no 2 people the same
		if(findPerson(p))
			return false;	// person is already in settlement
		if (p.healthCondition().equals("Sick")) {
			Sick[] temp = new Sick[m_sickPeople.length + 1];
			for (int i = 0; i < m_sickPeople.length; ++i) {
				temp[i] = m_sickPeople[i];
			}
			temp[m_sickPeople.length] = (Sick) p;
			m_sickPeople = temp;
		}
		else {
			Person[] temp = new Person[m_healthyPeople.length + 1];
			for (int i = 0; i < m_healthyPeople.length; ++i) {
				temp[i] = m_healthyPeople[i];
			}
			temp[m_healthyPeople.length] = p;
			m_healthyPeople = temp;
		}
		p.setSettlement(this); // change Settlement
		setRamzorColor(calculateRamzorGrade());
		return true;
	}

	/**
	 * removes a dead Person from Settlement
	 * 
	 * @param p - Person to delete
	 * @return true if Person deleted successfully
	 */
	public synchronized boolean removePerson(Person p) {
		if (!findPerson(p))
			return false; // person is not in settlement
		if (p.healthCondition().equals("Sick")) {
			Sick[] temp = new Sick[m_sickPeople.length - 1]; // decrease 1 in size
			int j = 0;
			for (int i = 0; i < m_sickPeople.length; ++i) {
				if (!(m_sickPeople[i].equals(p))) {
					temp[j] = m_sickPeople[i];
					++j;
				}
			}
			m_sickPeople = temp;
		}
		else
		{
			int j = 0;
			Person[] temp = new Person[m_healthyPeople.length - 1]; // decrease 1 in size
			for (int i = 0; i < m_healthyPeople.length; ++i) {
				if (!(m_healthyPeople[i].equals(p))) {
					temp[j] = m_healthyPeople[i];
					++j;
				}
			}
			m_healthyPeople = temp;
		}
		setRamzorColor(calculateRamzorGrade());
		return true;
	}

	/**
	 * checks if a certain Person is in Settlement
	 * @param p - a Person to search
	 * @return true if the person already exists in the settlement
	 */
	private boolean findPerson(Person p) {
		if(m_sickPeople.length != 0) {
			if(p.healthCondition().equals("Sick")) {
				for (int i = 0; i < m_sickPeople.length; ++i) {
					if (m_sickPeople[i].equals(p))
						return true;
				}
			}
		}
		if(m_healthyPeople.length != 0) {
			for (int i = 0; i < m_healthyPeople.length; ++i) {
				if (m_healthyPeople[i].equals(p))
					return true;
			}
		}
		return false;
	}// sync this method //

	/**
	 * run over the sick people and try to kill them
	 */
	private synchronized void attemptedMurder() {
		for(int i = 0; i<m_sickPeople.length;++i) {
			if(m_sickPeople[i].getVirusFromPerson().tryToKill(m_sickPeople[i])) {
				removePerson(m_sickPeople[i]);
				++m_numOfDeceased;
			}			
		}
	}

	/**
	 * calls toSting method for all Persons in Settlement
	 * @return toString of all the residents in the Settlement
	 */
	private String toStringPeople() {
		String str = "\n-- residents -- \n";
		// sync //
		for (int i = 0; i < m_healthyPeople.length; ++i)
			str += m_healthyPeople[i].toString() + "\n";
		for (int i = 0; i < m_sickPeople.length; ++i)
			str += m_sickPeople[i].toString() + "\n";
		// sync //
		return str;
	}

	/**
	 * move a Person from one Settlement to another
	 * @param p - person to transfer
	 * @param s - new settlement to transfer Person into
	 * @return true if successfully transferred
	 */
	private boolean transferPerson(Person p, Settlement s) {
		Settlement s1, s2;
		if(System.identityHashCode(this) < System.identityHashCode(s)) {
			s1 = this;
			s2 = s;
		}
		else {
			s1 = s;
			s2 = this;
		}
		synchronized (s1) {
			synchronized (s2) {
				if(this.equals(s))
					return false;
				if (s.m_maxPopulation <= getNumOfPeople())
					return false;
				Random ran = new Random();
				if ((getRamzorColor().getTransferProb() * s.getRamzorColor().getTransferProb()) >= ran.nextDouble()) // [0, 1) 
					return false;
				if (removePerson(p)) {
					s.addPerson(p);
					p.setSettlement(s);
					return true; 
				}
				return false;
			}
		}
	}

	/**
	 * tries to transfer random 3% of the Settlement to a random Settlement
	 * 
	 * @param randomSettlement - random Settlement to transfer to
	 */
	private synchronized Person[] randomTransfer(Settlement randomSettlement) { 
		// try transfer 3% from settlement

		int size = getNumOfPeople();
		Person[] temp = new Person[size];
		for (int i = 0; i < m_healthyPeople.length; ++i) {
			temp[i] = m_healthyPeople[i];
		}
		for (int i = 0; i < m_sickPeople.length; ++i) {
			temp[m_healthyPeople.length + i] = m_sickPeople[i];
		}
		Random ran = new Random();
		int random;
		int threePercent = (int) (size * 0.03);
		Person[] persons = new Person[threePercent];
		for (int i = 0; i < threePercent; ++i) {
			random = ran.nextInt(size);
			persons[i] = temp[random];
		}
		return persons;
	}


	/**
	 * vaccinate the population
	 */
	private synchronized void vaccineTime() {
		int index = 0 ;
		while(getVaccineDoses() > 0 && index != m_healthyPeople.length) {
			if (m_healthyPeople[index].healthCondition().equals("Healthy")) {
				((Healthy) m_healthyPeople[index]).vaccinate();
				--m_vaccineDoses;
			}
			++index;
		}
	}

	/**
	 * infects 1 percent of the population in each of the Settlements
	 */
	public void infectPercent(double num) {
		int amountToInfect = (int) (m_healthyPeople.length * num);
		int randomIndex;
		Random ran = new Random();
		VirusFactory vs = new VirusFactory();
		IVirus virus;

		for(int i = 0; i < amountToInfect; ++i) {
			randomIndex = ran.nextInt(m_healthyPeople.length);
			if (randomIndex % 3 == 0)
				virus = vs.createVirus("Chinese Variant");
			else if (randomIndex % 3 == 1)
				virus = vs.createVirus("British Variant");
			else
				virus = vs.createVirus("South African Variant");
			try {
				m_healthyPeople[randomIndex].contagion(virus);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	} 

	/**
	 * 
	 * @return a random Settlement from the Connections array
	 */
	private Settlement randomConnection() {
		Random ran = new Random();
		// sync //
		if (m_connectedSettlements.length == 0)
			return null;
		return m_connectedSettlements[ran.nextInt(m_connectedSettlements.length)];
		// sync //
	}

	/**
	 * one simulation operation
	 */
	private synchronized void simulation() {
		Random ran = new Random();
		int tempIndex = (int) (m_sickPeople.length * 0.2);
		for (int j = 0; j < tempIndex; ++j) {// run over the population of each settlement
			randomContagion(m_sickPeople[ran.nextInt(tempIndex)]);
		}
	}

	/**
	 * chooses randomly six people to try to infect for each sick Person currently
	 * in the Settlement
	 * 
	 * @param sickPerson - array of sick people
	 */
	private void randomContagion(Person sickPerson) {
		IVirus virus = null;
		Random ran = new Random();
		VirusManager vm = VirusManager.SingeltonVirusManager();
		for (int i = 0; i < 3; ++i) {
			if (m_healthyPeople.length == 0)
				return;
			int randomIndex = ran.nextInt(m_healthyPeople.length);
			virus = vm.createVirus(sickPerson.getVirusFromPerson());
			if (virus == null)
				return;
			if (virus.tryToContagion(sickPerson, m_healthyPeople[randomIndex])) {
				m_healthyPeople[randomIndex].contagion(virus);
			}
		}
	}

	/**
	 * 
	 * @return point of the middel of the settlements
	 */
	public Point middelOfSettlement() {
		int x = (int) (m_location.getPoint().getX() + (m_location.getSize().getWidth() / 2));
		int y = (int) (m_location.getPoint().getY() + (m_location.getSize().getHeith() / 2));
		return new Point(x, y);
	}


	/**
	 * 
	 * @param s - settlement to connect to the current settlement
	 */
	public void addNewConnection(Settlement s) {
		Settlement[] temp = new Settlement[m_connectedSettlements.length + 1];
		for (int i = 0; i < m_connectedSettlements.length; ++i) {
			temp[i] = m_connectedSettlements[i];
		}
		temp[m_connectedSettlements.length] = s;
		m_connectedSettlements = temp;
	} // sync this method//

	/**
	 *  if past 25 days since the sick person got infected - recovery him
	 */
	private synchronized void sickToConvalescent() {
		for (int i = 0; i < m_sickPeople.length; ++i) {
			if (Clock.calculateDays(m_sickPeople[i].getContagiousTime()) > m_recoveryTime) {
				m_sickPeople[i].recover();
			}
		}
	} 

	/**
	 * 
	 * @return middle Points connections of a settlement array 
	 */
	public Point[] conectionsPoints() {
		Point[] settlPoints = new Point[0];	
		for (int i = 0; i < m_connectedSettlements.length; ++i) {
			Point middel = m_connectedSettlements[i].middelOfSettlement();
			settlPoints = addToConectionsPoints(settlPoints, middel);
		}
		return settlPoints;
	}

	/**
	 * 
	 * @param arr - array of Point
	 * @param p - new Point
	 * @return new Point array with the new Point
	 */
	private Point[] addToConectionsPoints(Point[] arr, Point p) {
		Point[] temp = new Point[arr.length + 1];
		for(int i=0 ; i < arr.length ; ++i) 
			temp[i] = arr[i];
		temp[arr.length] = p;
		return temp;
	}



	/**
	 * get method
	 * @return current RamzorColor
	 */
	public RamzorColor getRamzorColor() {
		return m_ramzorColor;
	}

	/**
	 * get method
	 * 
	 * @return Settlement name
	 */
	public String getSettlementName() {
		return m_name;
	}

	/**
	 * 
	 * @return total population in settlement
	 */
	public int getNumOfPeople() {
		return m_sickPeople.length + m_healthyPeople.length;
	} // sync this method //

	/**
	 * set method
	 * @param r - new RamzorColor of the settlement
	 */
	protected void setRamzorColor(RamzorColor r) {
		m_ramzorColor = r;
	}

	/**
	 * 
	 * @return String of Settlement type
	 */
	public abstract String getSettlementType();

	/**
	 * 
	 * @return the number of the vaccines in the settlement
	 */
	public int getVaccineDoses() {
		return m_vaccineDoses;
	}

	/**
	 * set the number of the deceased
	 */
	public void setNumOfDeceased() {
		++m_numOfDeceased;
	}

	/**
	 * 
	 * @return the number of the deceased
	 */
	public int getNumOfDeceased() {
		return m_numOfDeceased;
	}

	/**
	 * set the number of the vaccine doses
	 * @param amount - the new number of the vaccine doses
	 */
	public void setVaccineDoses(int amount) {
		if (amount >= 0)
			m_vaccineDoses += amount;
	}

	/**
	 * 
	 * @return the location of the settlement
	 */
	public Location getLocation() {
		return new Location(m_location);
	}

	/**
	 * 
	 * @return the connection of the settlement
	 */
	public Settlement[] getConnection() {
		return m_connectedSettlements;
	}

	/**
	 * 
	 * @return percent of deceased in Settlement
	 */
	private double deceasedPercent() {
		return m_numOfDeceased / (double) getNumOfPeople();
	} // sync this method//

	/**
	 * 
	 * @param oldPercent - the amount of deceased before attempted murder
	 * @return true if deceased percent is 1% from the Population
	 */
	private boolean isDeceasedOnePercent() {
		if (deceasedPercent() >= m_lastLog + 0.01 ) {
			m_lastLog += 0.01;
			return true;
		}
		return false;
	}

	/**
	 * 
	 * @return String of all log information
	 */
	private String getLogInfo() {
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
		// sync //
		String s1 = "\n" + m_sickPeople.length;
		// sync //
		String info = LocalDateTime.now().format(dtf) + "\n" + getSettlementName() + s1 + "\n"
				+ m_numOfDeceased + "\n";
		return info;
	}

	/**
	 * writes logInfo to file or to Vector
	 */
	private void saveToLogFile() {
		if (m_map.getLogFlag()) {
			// call write string to logFile
			LogFile.exportToLog(getLogInfo(),m_map.getLogPath());
		}
	}


	private final int m_recoveryTime = 25; //the number of days that after this the sick person recovery
	private final String m_name;// Settlement's name
	private final Location m_location;// Settlement's Location
	private Person[] m_healthyPeople;// Settlement's healthy residents
	private RamzorColor m_ramzorColor;// Settlement's RamzorColor grade
	private int m_maxPopulation;// max residents in settlement
	private int m_vaccineDoses; // num of vaccine doses
	private Settlement[] m_connectedSettlements;// all the connections to current settlement
	private Sick[] m_sickPeople;// Settlement's sick residents
	private int m_numOfDeceased;// counts deaths in Settlement
	private Map m_map;
	private double m_lastLog = 0;
}
