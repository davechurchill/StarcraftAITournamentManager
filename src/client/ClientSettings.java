package client;

import java.io.BufferedReader;
import java.io.FileReader;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import objects.BWAPISettings;

public class ClientSettings
{	
	public String			ClientStarcraftDir;
	
	public String			TournamentModuleFilename;
	
	public String			ServerAddress;
	public String 			DefaultBWAPISettingsFileName;
	
	public BWAPISettings	bwapi = new BWAPISettings();

	private static final ClientSettings INSTANCE = new ClientSettings();
	
	private ClientSettings()
	{
		
	}
	
	public static ClientSettings Instance() 
	{
        return INSTANCE;
    }
	
	public void parseSettingsFile(String filename)
	{
		try
		{
			BufferedReader br = new BufferedReader(new FileReader(filename));
			JsonObject jo = Json.parse(br).asObject();
			br.close();
			
			ClientStarcraftDir = jo.get("ClientStarcraftDir").asString();
			System.out.println("StarCraft Dir:   " + ClientStarcraftDir);
			DefaultBWAPISettingsFileName = jo.get("DefaultBWAPISettings").asString();
			TournamentModuleFilename = jo.get("TournamentModule").asString();
			ServerAddress = jo.get("ServerAddress").asString();		
		}
		catch (Exception e)
		{
			System.err.println("Error parsing settings file, exiting\n");
			e.printStackTrace();
			System.exit(-1);
		}
		
		bwapi.loadFromFile(DefaultBWAPISettingsFileName);
	}
}