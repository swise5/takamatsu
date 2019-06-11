package utilities;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import com.vividsolutions.jts.geom.Point;

import myobjects.Household;
import myobjects.Person;
import mysim.TakamatsuSim;
import mysim.TakamatsuSim.MediaInstance;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;

import sim.engine.Schedule;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.field.geo.GeomVectorField;
import sim.util.Bag;
import sim.util.geo.AttributeValue;
import sim.util.geo.MasonGeometry;
import swise.objects.PopSynth;

public class PersonUtilities {
	
	public static synchronized ArrayList<Person> setupPersonsAtRandom(GeomVectorField buildings, Schedule schedule, TakamatsuSim world, 
			GeometryFactory fa){
		
		ArrayList <Person> agents = new ArrayList <Person> ();
		Bag myBuildings = buildings.getGeometries();
		int myBuildingsSize = myBuildings.numObjs;
		
		for(int i = 0; i < 1000; i++){
			
			Object o = myBuildings.get(world.random.nextInt(myBuildingsSize));
			MasonGeometry mg = (MasonGeometry) o;
			while(mg.geometry.getArea() > 1000){
				o = myBuildings.get(world.random.nextInt(myBuildingsSize));
				mg = (MasonGeometry) o;
			}
			//Point myPoint = mg.geometry.getCentroid();
			//Coordinate myC = new Coordinate(myPoint.getX(), myPoint.getY());
			Coordinate myC = (Coordinate) mg.geometry.getCoordinate().clone();
			Person a = new Person("Person"+i,myC,myC,myC, null, world);
			agents.add(a);
			
			world.schedule.scheduleOnce(a);
		}
		
		return agents;
	}
	
	public static synchronized ArrayList<Person> setupHouseholdsAtRandom(GeomVectorField buildings, Schedule schedule, TakamatsuSim world, 
			GeometryFactory fa){
		
		ArrayList <Person> agents = new ArrayList <Person> ();
		Bag myBuildings = buildings.getGeometries();
		int myBuildingsSize = myBuildings.numObjs;
		int personIndex = 0;
		
		for(Object o: buildings.getGeometries()){
			
			// check the geometry: if it's huge, then it's not a house!
			MasonGeometry mg = (MasonGeometry) o;
			if(mg.geometry.getArea() > 1000)
				continue;

			// create the household
			Coordinate home = (Coordinate)mg.geometry.getCoordinate().clone();
			Household h = new Household(home);
			
			// set up Household members
			int numChildren = (int) Math.max(0, 2 * world.random.nextGaussian());
			int numAdults = (int) (1 + world.random.nextInt(6));
			for(int i = 0; i < numAdults; i++)
				h.addMember(new Person("ID_"+personIndex++,home, null, home, h, world));
			for(int i = 0; i < numChildren; i++)
				h.addMember(new Person("ID_child_"+personIndex++,home, null, home, h, world));

			// schedule all to update
			for(Person p: h.getMembers())
				world.schedule.scheduleOnce(p);
			
			// save to records
			agents.addAll(h.getMembers());
		}
		
		for(int i = 0; i < 1000; i++){
			
			Object o = myBuildings.get(world.random.nextInt(myBuildingsSize));
			MasonGeometry mg = (MasonGeometry) o;
			while(mg.geometry.getArea() > 1000){
				o = myBuildings.get(world.random.nextInt(myBuildingsSize));
				mg = (MasonGeometry) o;
			}
			//Point myPoint = mg.geometry.getCentroid();
			//Coordinate myC = new Coordinate(myPoint.getX(), myPoint.getY());
			Coordinate myC = (Coordinate) mg.geometry.getCoordinate().clone();
			Person a = new Person("Person"+i,myC,myC,myC, null, world);
			agents.add(a);
			
		}
		
		return agents;
	}
	
	public static synchronized ArrayList<Person> setupPersonsFromFile(String agentsFilename, Schedule schedule, TakamatsuSim world, MediaInstance media){
		try {
			ArrayList<Person> agents = new ArrayList <Person> ();
			
			System.out.println("Reading in agents from " + agentsFilename);
			
			// Open the tracts file
			FileInputStream fstream = new FileInputStream(agentsFilename);

			// Convert our input stream to a BufferedReader
			BufferedReader agentData = new BufferedReader(new InputStreamReader(fstream));
			String s;


			System.out.println("BEGIN READING IN PEOPLE");

			HashMap <Person, HashMap <String, Integer>> socialNetwork = new HashMap <Person, HashMap <String, Integer>> ();
			HashMap <Person, ArrayList <String>> socialMediaNetwork = new HashMap <Person, ArrayList <String>> ();
			HashMap <String, Person> agentNameMapping = new HashMap <String, Person> ();
			
			int indexy = -1;
			while ((s = agentData.readLine()) != null) {
				String[] bits = s.split("\t");
				
				indexy++;
				
				// recreate the Person from the record
				String id = bits[0];
				Integer age = Integer.parseInt(bits[1]);
				Integer sex = Integer.parseInt(bits[2]);
				Coordinate homeCoord = InputCleaning.readCoordinateFromFile(bits[3]);
				Coordinate workCoord = InputCleaning.readCoordinateFromFile(bits[4]);
				
				Person a = new Person(id, homeCoord, homeCoord, workCoord, null, world);
				
				a.addIntegerAttribute("sex", sex);
				a.addIntegerAttribute("age", age);
				agentNameMapping.put(id, a);

				agents.add(a);

				// SOCIAL NETWORK GENERATION
				
				// store social information for complete creation later
				socialNetwork.put(a, new HashMap <String, Integer> ());
				Integer networkSize = Integer.parseInt(bits[5]);
				for(int i = 0; i < networkSize; i++){
					int index = i + 6;
					String [] contact = bits[index].split(" ");
					String contactName = contact[0];
					Integer contactWeight = (int) (10 * Double.parseDouble(contact[1]));
					
					// TODO: possibly change back
					if(contactWeight > PopSynth.acquaintenceWeight)
						socialNetwork.get(a).put(contactName, contactWeight);
				}

				
				// SOCIAL MEDIA NETWORK GENERATION
				
				if(bits.length <= networkSize + 6) 
					continue;
				
				socialMediaNetwork.put(a, new ArrayList <String> ());
				Integer mediaNetworkSize = Integer.parseInt(bits[networkSize + 6]);
				for(int i = 0; i < mediaNetworkSize; i++){
					int index = i + 7 + networkSize;
					String contactName = bits[index]; 
					socialMediaNetwork.get(a).add(contactName);
				}
			}

			agentData.close();

			System.out.println("REINFLATING SOCIAL TIES...");

			indexy = -1;
			// reinflate the stored social network from the records
			for(Person a: socialNetwork.keySet()){

				indexy++;
				if(indexy % 1000 == 0)
					System.out.println("\t" + indexy + "...");

				for(Entry e: socialNetwork.get(a).entrySet()){
					String contact = (String) e.getKey();
					int weight = (Integer) e.getValue();
					Person b = agentNameMapping.get(contact);
					if(a == null || b == null) continue;
					a.addContact(b, weight);
					b.addContact(a, weight);
				}				
			}
			
			System.out.println("REINFLATING MEDIA TIES...");

			indexy = -1;
			// reinflate the stored social media network from the records
			for(Person a: socialMediaNetwork.keySet()){

				indexy++;
				if(indexy % 1000 == 0)
					System.out.println("\t" + indexy + "...");

				for(String b: socialMediaNetwork.get(a)){
					Person c = agentNameMapping.get(b);
					if(c == null) continue;
					a.addSocialMediaContact(c);
					c.addSocialMediaContact(a);
				}
			}
			
			for(Person a: agents){
				// EVERYONE IS IN TOUCH WITH MASS MEDIA! (social media just amplifies the signal)
				a.addSocialMediaContact(media);
			}
			
			System.out.println("DONE READING IN PEOPLE");
			// clean up
			
			
			schedule.scheduleRepeating(1316, new Steppable(){

				@Override
				public void step(SimState state) {
					((TakamatsuSim)state).resetLayers();
					
				}
				
			});
			
			return agents;

		} catch (Exception e) {
			System.err.println("File input error: " + agentsFilename);
		}
		return null;
	}
	
	/**
	 * Given a record file of a set of Persons, create Persons with the assigned characteristics
	 * and add them to the simulation
	 * 
	 * @param agentsFilename - the file in which the agent records are stored
	 */
	public static synchronized ArrayList<Person> setupHouseholdsFromFile(String agentsFilename, Schedule schedule, TakamatsuSim world){
		try {
			ArrayList<Person> agents = new ArrayList <Person> ();
			
			System.out.println("Reading in agents from " + agentsFilename);
			
			// Open the tracts file
			FileInputStream fstream = new FileInputStream(agentsFilename);

			// Convert our input stream to a BufferedReader
			BufferedReader agentData = new BufferedReader(new InputStreamReader(fstream));
			String s;

			// assmeble the list of building names so that the agents can be associated with the correct buildings
			HashMap <String, MasonGeometry> buildingNames = new HashMap <String, MasonGeometry> ();
			for(Object o: world.buildingLayer.getGeometries()){
				MasonGeometry mg = (MasonGeometry) o;
				buildingNames.put(mg.getStringAttribute("fid"), mg);
				Coordinate c = world.snapPointToRoadNetwork(mg.geometry.getCoordinate());
				mg.addAttribute("entrance", c);
			}

			// with that established, read in the households from the file
			System.out.println("BEGIN READING IN HOUSEHOLDS");

			HashMap <String, Person> agentNameMapping = new HashMap <String, Person> ();
			
			while ((s = agentData.readLine()) != null) {
				String[] bits = s.split("\t");
				
				// recreate the Person from the household records
				String id = bits[0];
				
				// identify the building where the Household lives
				String buildingName = bits[1];
				MasonGeometry myHomeBuilding = buildingNames.get(buildingName);
				if(myHomeBuilding == null){
					System.out.println("ERROR when reading in Household " + id + ": no building associated");
					continue;
				}

				// with the location, set up the Household object to hold the Persons
				AttributeValue myEntrance = (AttributeValue) myHomeBuilding.getAttribute("entrance");
				Coordinate homeCoord = (Coordinate) myEntrance.getValue();//myHomeBuilding.geometry.getCoordinate();
				Coordinate homeCoordCopy = new Coordinate(homeCoord.x, homeCoord.y, homeCoord.z);
				Household h = new Household(homeCoordCopy);

				// determine how many Household members there are
				Integer numHouseholdMembers = Integer.parseInt(bits[2]);
								
				// read in each Household member and add them to the Household
				for(int i = 3; i < numHouseholdMembers; i++){

					String [] raw_person = bits[i].split(":");
					
					String p_id = raw_person[0];
					Integer age = Integer.parseInt(raw_person[1]);
					Integer sex = Integer.parseInt(raw_person[2]);

					homeCoordCopy = new Coordinate(homeCoord.x, homeCoord.y, homeCoord.z);
					Person a = new Person(p_id, homeCoordCopy, homeCoordCopy, null, h, world);
					
					a.addIntegerAttribute("sex", sex);
					a.addIntegerAttribute("age", age);
					agentNameMapping.put(id, a);

					agents.add(a);
					world.schedule.scheduleOnce(a);
				}
			}

			agentData.close();


			System.out.println("DONE READING IN PEOPLE");
			// clean up
			
			
			schedule.scheduleRepeating(1316, new Steppable(){

				@Override
				public void step(SimState state) {
					((TakamatsuSim)state).resetLayers();
					
				}
				
			});
			
			return agents;

		} catch (Exception e) {
			System.err.println("File input error: " + agentsFilename);
		}
		return null;
	}
	
}