package mysim;

public class BulkSim {
	
	public static void main(String [] args){
		for(int i = 0; i < 30; i++){
			TakamatsuSim.main(new String [] {});
			System.gc();
		}
	}
}