package objects;

import java.io.Serializable;
import java.util.Vector;

import server.ServerSettings;

public class Bot implements Serializable
{
	private static final long serialVersionUID = 2690734629985126222L;
	private String name;
	private String race;
	private String type;
	private String bwapiVersion;
	private Vector<String> requirements;
	private boolean valid = true;

	public Bot(String name, String race, String type, String bwapiVersion)
	{
		this.name = name;
		this.race = race;
		this.type = type;
		this.bwapiVersion = bwapiVersion;
	}
	
	public Bot(String name, String race, String type, String bwapiVersion, Vector<String> requirements)
	{
		this.name = name;
		this.race = race;
		this.type = type;
		this.bwapiVersion = bwapiVersion;
		this.requirements = requirements;
	}
	
	public String getName() 
	{
		return name;
	}

	public String getRace() 
	{
		return race;
	}
	
	public void setRace(String race)
	{
		this.race = race;
	}
	
	public String getType()
	{
		return type;
	}
	
	public void setType(String type)
	{
		this.type = type;
	}
	
	public String getServerDir()
	{
		return ServerSettings.Instance().ServerBotDir + getName() + "/";
	}
	
	public String getBWAPIVersion()
	{
		return bwapiVersion;
	}
	
	public void setBWAPIVersion(String version)
	{
		this.bwapiVersion = version;
	}
	
	public boolean isProxyBot()
	{
		return type.equalsIgnoreCase("proxy");
	}
	
	public void setValid(boolean valid)
	{
		this.valid = valid;
	}
	
	public boolean isValid()
	{
		return valid;
	}
	
	public boolean hasRequirements()
	{
		if (requirements == null)
		{
			return false;
		}
		return true;
	}
	
	public Vector<String> getRequirements()
	{
		return requirements;
	}
	
	public void setRequirements(Vector<String> requirements)
	{
		this.requirements = requirements;
	}
}
