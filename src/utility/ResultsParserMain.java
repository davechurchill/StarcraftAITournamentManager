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
			
			String headerHTML = rp.getHeaderHTML();
			String footerHTML = rp.getFooterHTML();
			String resultsHTML = rp.getResultsHTML();
			
			FileUtils.writeToFile(headerHTML + resultsHTML + footerHTML, "html/index.html");
			//FileUtils.writeToFile(rp.getResultsJSON(), "html/results_summary_json.txt");
			
			rp.writeWinPercentageGraph();
			rp.writeDetailedResultsHTML();
			//rp.writeDetailedResultsJSON();
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}
