package objects;

public class ClientStatusMessage implements Message
{
	private static final long serialVersionUID = 3747113650323312712L;
	
	public ClientStatus 				status 			= null;
	public GameReport 					report 			= null;
	public boolean 						isHost 			= false;
	public TournamentModuleState 		gameState 		= null;
	public int 							startingTime 	= 0;

	public ClientStatusMessage(ClientStatus status, GameReport report) 
	{
		this.status = status;
		this.report = report;
	}
	
	public ClientStatusMessage(ClientStatus status, GameReport report, TournamentModuleState gs, boolean isHost, int startingTime) 
	{
		this.status = status;
		this.report = report;
		this.gameState = gs;
		this.isHost = isHost;
		this.startingTime = startingTime;
	}
	
	public String toString()
	{
		return status.toString();
	}
}
