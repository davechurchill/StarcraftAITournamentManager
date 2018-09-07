package client;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Vector;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import objects.BWAPISettings;

public class ClientSettings
{	
	public String			ClientStarcraftDir;
	
	public String			TournamentModuleFilename;
	
	public String			ServerAddress;
	
	Vector<String>			ClientProperties;

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
			TournamentModuleFilename = jo.get("TournamentModule").asString();
			ServerAddress = jo.get("ServerAddress").asString();
			
			ClientProperties = new Vector<String>();
			JsonValue propertiesArray = jo.get("ClientProperties");
			if (propertiesArray != null)
			{
				JsonArray properties = propertiesArray.asArray();
				for (JsonValue property : properties)
				{
					ClientProperties.add(property.asString());
				}
			}
		}
		catch (Exception e)
		{
			System.err.println("Error parsing settings file, exiting\n");
			e.printStackTrace();
			System.exit(-1);
		}
	}
}