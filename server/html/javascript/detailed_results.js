$(function()
{
	//these arguments are declared in another file output by the Tournament Manager
	fillDetailedResultsTable(detailedResults, replayPath);
});

function fillDetailedResultsTable(data, replayDir)
{
	var html = "";
	
	var winnerTimerHeaders = "";
	var loserTimerHeaders = "";
	
	var numResults = data.length;
	if (numResults > 0)
	{
		var numTimers = data[0].WinnerTimers.length;
		for (var i = 0; i < numTimers; i++)
		{
			winnerTimerHeaders += "<th> W " + data[0].WinnerTimers[i].Limit + "</th>";
			loserTimerHeaders += "<th> L " + data[0].LoserTimers[i].Limit + "</th>";
		}
	}
	
	//headers
	var headerHtml = "<tr><th>Round/Game</th><th>Winner</th><th>Loser</th><th>Crash</th><th>Timeout</th><th>Map</th><th>Duration</th><th>W Score</th><th>L Score</th><th>(W-L)/Max</th>";
	headerHtml += winnerTimerHeaders + loserTimerHeaders;
	headerHtml += "<th>Win Addr</th><th>Lose Addr</th><th>Start</th><th>Finish</th></tr>";
	
	$("#detailedResultsTable thead").html(headerHtml);
	
	for (var i=0; i<numResults; ++i)
	{
		html += "<tr>";
		html += "<td>"+ data[i]['Round/Game'] + "</td>"; 
		
		if (data[i].WinnerReplay != "")
		{
			html += "<td><a href='" + replayDir + data[i].WinnerReplay + "'>" + data[i].WinnerName + "</a></td>";
		}
		else
		{
			html += "<td>" + data[i].WinnerName + "</td>";
		}
		
		if (data[i].LoserReplay != "")
		{
			html += "<td><a href='" + replayDir + data[i].LoserReplay + "'>" + data[i].LoserName + "</a></td>";
		}
		else
		{
			html += "<td>" + data[i].LoserName + "</td>";
		}
		
		html += "<td>" + data[i].Crash + "</td>";
		html += "<td>" + data[i].Timeout + "</td>";
		html += "<td>" + data[i].Map + "</td>";
		html += "<td>" + data[i].Duration + "</td>";
		html += "<td>" + data[i]['W Score'] + "</td>";
		html += "<td>" + data[i]['L Score'] + "</td>";
		html += "<td>" + data[i]['(W-L)/Max'] + "</td>";
		
		for (var j = 0; j < numTimers; j++)
		{
			html += "<td>" + data[i].WinnerTimers[j].Count + "</td>";
		}
		for (var j = 0; j < numTimers; j++)
		{
			html += "<td>" + data[i].LoserTimers[j].Count + "</td>";
		}
		
		html += "<td>" + data[i]['Win Addr'] + "</td>";
		html += "<td>" + data[i]['Lose Addr'] + "</td>";
		html += "<td>" + data[i].Start + "</td>";
		html += "<td>" + data[i].Finish + "</td>";
		
		html += "</tr>";
	}
	$("#detailedResultsTable tbody").html(html);
}