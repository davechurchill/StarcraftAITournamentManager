package objects;

import java.io.File;
import java.io.Serializable;

public class Map implements Serializable
{
	private static final long serialVersionUID = 4103173912768231773L;

	private String mapName;

	private String mapLocation;

	public Map(String mapLocation) 
	{
		this.mapLocation = mapLocation;
		
		File f = new File(mapLocation);
		mapName = f.getName();
	}

	public String getMapName() 
	{
		return mapName;
	}

	public String getMapLocation() 
	{
		return mapLocation;
	}

	public void print() 
	{
		System.out.println(this.mapName + " -> " + this.mapLocation);
	}
}
