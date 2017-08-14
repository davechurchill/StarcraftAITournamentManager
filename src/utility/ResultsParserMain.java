package utility;

import server.ServerSettings;

public class ResultsParserMain
{
	public static String serverSettingsFile;
	
	public static void main(String[] args) throws Exception
	{
		if (args.length == 1)
		{
			ServerSettings.Instance().parseSettingsFile(args[0]);
		}
		else
		{
			System.err.println("\n\nPlease provide server settings file as command line argument.\n");
			System.exit(-1);
		}
		
		writeHTMLFiles();
		
	}
	
	public synchronized static void writeHTMLFiles() throws Exception
	{
		try
		{
			ResultsParser rp = new ResultsParser(ServerSettings.Instance().ResultsFile);
			
			FileUtils.writeToFile(rp.getResultsJSON(), "html/results/results_summary_json.js");
			rp.writeWinPercentageGraph();
			rp.writeDetailedResultsJSON();
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}
