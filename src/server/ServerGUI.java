
package server;

import java.awt.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;

import objects.Bot;
import utility.FileUtils;
import utility.GameListGenerator;
import utility.ResultsParser;

import java.awt.event.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerGUI 
{
	Server		server;
	
    private 	JFrame			mainFrame;
    private 	JTable			mainTable;
    private 	JTextArea		bottomText;
    private 	JPanel			bottomPanel;
    private		JPanel			statusPanel;
    private		JProgressBar	progressBar;
    private		JLabel			uptime;
    private 	JMenuBar		menuBar;
    private 	JMenu			fileMenu;
    private 	JMenu			actionsMenu;
    private		JMenuItem		exitMenuItem;
    private 	JMenuItem		generateResultsMenuItem;
    private 	JMenuItem		sendClientCommandMenuItem;
    private		JMenuItem		viewClientScreenMenuItem;
    private		JPopupMenu		popup;
    
    private String [] 		columnNames = {"Client", "Status", "Game / Round #", "Self", "Enemy", "Map", "Duration", "Win", "Properties"};
	private Object [][] 	data = 	{ };
	private Date startTime;
	private String filter = "";
	private	Vector<String> log;
    private	JComboBox<String> filterSelect;

	private boolean resumedTournament = false;
	
	public ServerGUI(Server server)
	{
		this.server = server;
		CreateGUI();
	}
	
	public void CreateGUI()
	{
		mainTable = new JTable(new MainTableModel(data, columnNames));
		mainTable.setDefaultRenderer(Object.class, new MyRenderer());
		mainTable.setFillsViewportHeight(true);
		mainTable.getTableHeader().setReorderingAllowed(false);
		
		mainFrame = new JFrame("StarCraft AI Tournament Manager - Server");
		mainFrame.setLayout(new GridBagLayout());
		
		statusPanel = new JPanel();
		statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.X_AXIS));
		statusPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		
		statusPanel.add(new JLabel("Tournament progress: "));
		progressBar = new JProgressBar(0, 100);
		progressBar.setFont(new Font(progressBar.getFont().getFamily(), Font.PLAIN, progressBar.getFont().getSize()));
		statusPanel.add(progressBar);
		
		statusPanel.add(new JLabel(" Server uptime: "));
		uptime = new JLabel("");
		uptime.setFont(new Font(uptime.getFont().getFamily(), Font.PLAIN, uptime.getFont().getSize()));
		statusPanel.add(uptime);
		
		statusPanel.add(Box.createHorizontalGlue());
		
		statusPanel.add(new JLabel("Filter Log: "));
		filterSelect = new JComboBox<String>();
		filterSelect.setFont(new Font(filterSelect.getFont().getFamily(), Font.PLAIN, filterSelect.getFont().getSize()));
		filterSelect.addItem("All");
		filterSelect.addItem("Server");
		statusPanel.add(filterSelect);
		
		bottomText = new JTextArea();
		bottomText.setEditable(false);
		log = new Vector<String>();
		
		bottomPanel = new JPanel();
		bottomPanel.setLayout(new GridLayout(1,0));
		bottomPanel.add(new JScrollPane(bottomText));
	
        popup = new JPopupMenu();
        
        JMenuItem popupScreenshotMenuItem = new JMenuItem("Take Client Screenshot");
        popupScreenshotMenuItem.addActionListener(new ActionListener()
        {
			public void actionPerformed(ActionEvent e)
			{
				for (int r : mainTable.getSelectedRows())
				{
					server.sendScreenshotRequestToClient((String) mainTable.getValueAt(r, 0));
				}
			}
        });
        popup.add(popupScreenshotMenuItem);
        
        JMenuItem popupSendCommandMenuItem = new JMenuItem("Send Command");
        popupSendCommandMenuItem.addActionListener(new ActionListener()
        {
			public void actionPerformed(ActionEvent e)
			{
				String command = (String)JOptionPane.showInputDialog(mainFrame, 
        				"Enter Windows command to be executed on the selected Client machine(s).\n\n"
        				+ "Will run as if typed into the client's Windows command line.\n\n"
        				+ "Execution is asynchronous to client, no error on failure.\n\n"
        				+ "Example:     notepad.exe\n"
        				+ "Example:     taskkill /im notepad.exe\n\n", 
        				"Send Command to Client(s)", JOptionPane.PLAIN_MESSAGE, null, null, "");
        	
	        	if (command != null && command.trim().length() > 0)
	        	{
	        		for (int r : mainTable.getSelectedRows())
					{
	        			server.sendCommandToClient((String) mainTable.getValueAt(r, 0), command);
					}
	        	}
			}
        });
        popup.add(popupSendCommandMenuItem);
        
        JMenuItem popupKillClientMenuItem = new JMenuItem("Kill Client");
        popupKillClientMenuItem.addActionListener(new ActionListener()
        {
        	public void actionPerformed(ActionEvent e)
			{
        		int confirmed = JOptionPane.showConfirmDialog(mainFrame, "Kill Selected Client(s): Are you sure?", "Kill Client(s) Confirmation", JOptionPane.YES_NO_OPTION);
    			if (confirmed == JOptionPane.YES_OPTION)
    			{
    				//get IP addresses before deleting rows (clients) from table
            		String[] addresses = new String[mainTable.getSelectedRowCount()];
            		int i = 0;
            		for (int r : mainTable.getSelectedRows())
    				{
            			addresses[i++] = (String) mainTable.getValueAt(r, 0);
    				}
            		
            		for (String ip : addresses)
            		{
            			server.killClient(ip);
            		}
    			}
			}
        });
        popup.add(popupKillClientMenuItem);
        
        JMenuItem popupFilterLog = new JMenuItem("Filter Log by Client");
        popupFilterLog.addActionListener(new ActionListener()
        {
        	public void actionPerformed(ActionEvent e)
			{
        		filter = (String) mainTable.getValueAt(mainTable.getSelectedRow(), 0);
        		for (int i = 0; i < filterSelect.getModel().getSize(); i++)
        		{
        			if (filterSelect.getItemAt(i).equalsIgnoreCase(filter))
        			{
        				filterSelect.setSelectedIndex(i);
        			}
        		}
			}
        });
        popup.add(popupFilterLog);
        
        class PopupListener extends MouseAdapter {
        	
        	public void mousePressed(MouseEvent e)
        	{
        		super.mousePressed(e);
        		if (mainTable.getRowCount() > 0)
        		{
        			int r = mainTable.rowAtPoint(e.getPoint());
            		if (r == -1)
            		{
            			mainTable.removeRowSelectionInterval(0, mainTable.getRowCount() - 1);
            		}
            		else if (SwingUtilities.isRightMouseButton(e) && !mainTable.isRowSelected(r))
            		{
    					mainTable.removeRowSelectionInterval(0, mainTable.getRowCount() - 1);
    					mainTable.setRowSelectionInterval(r, r);
            		}
        		}
        	}
        	
        	public void mouseReleased(MouseEvent e)
        	{
        		super.mouseReleased(e);
        		
        		if (SwingUtilities.isRightMouseButton(e) && mainTable.rowAtPoint(e.getPoint()) != -1)
        		{
        			popup.show(e.getComponent(), e.getX(), e.getY());
        		}
        	}
        }
        
        MouseListener popupListener = new PopupListener();
        mainTable.addMouseListener(popupListener);
	
        filterSelect.addActionListener(new ActionListener()
        		{
					public void actionPerformed(ActionEvent arg0)
					{
						filter = (String) filterSelect.getSelectedItem();
						filterLog();
					}
        		}
        );
        
        menuBar = new JMenuBar();
        fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        menuBar.add(fileMenu);
        actionsMenu = new JMenu("Actions");
        actionsMenu.setMnemonic(KeyEvent.VK_A);
        menuBar.add(actionsMenu);
        
        generateResultsMenuItem = new JMenuItem("Generate Detailed Results HTML", KeyEvent.VK_G);
        generateResultsMenuItem.addActionListener(new ActionListener() 
        {
            public void actionPerformed(ActionEvent e) 
            {
            	int confirmed = JOptionPane.showConfirmDialog(mainFrame, "Generate Detailed Results? This may take a while for large files.", "Detailed Results Confirmation", JOptionPane.YES_NO_OPTION);
    			if (confirmed == JOptionPane.YES_OPTION)
    			{
    				try
    				{
    					ResultsParser rp = new ResultsParser(ServerSettings.Instance().ResultsFile);
    					logText(getTimeStamp() + " Generating All Results File...\n");
    					rp.writeDetailedResultsJSON();
    					logText(getTimeStamp() + " Generating All Results File Complete!\n");
    				}
    				catch (Exception ex)
    				{
    					logText(getTimeStamp() + " Generating results failed :(\n");
    				}
    			}   
            }
        });
        actionsMenu.add(generateResultsMenuItem);

        exitMenuItem = new JMenuItem("Quit Server", KeyEvent.VK_Q);
        exitMenuItem.addActionListener(new ActionListener() 
        {
            public void actionPerformed(ActionEvent e) 
            {
            	int confirmed = JOptionPane.showConfirmDialog(mainFrame, "Shutdown Server: This will stop the entire tournament.", "Shutdown Confirmation", JOptionPane.YES_NO_OPTION);
    			if (confirmed == JOptionPane.YES_OPTION)
    			{
    				server.shutDown();
    			}   
            }
        });
        fileMenu.add(exitMenuItem);
        
        sendClientCommandMenuItem = new JMenuItem("Send Command to all Clients", KeyEvent.VK_C);
        sendClientCommandMenuItem.addActionListener(new ActionListener() 
        {
            public void actionPerformed(ActionEvent e) 
            {
            	String command = (String)JOptionPane.showInputDialog(mainFrame, 
            				"Enter Windows command to be executed on all Client machines.\n\n"
            				+ "Will run as if typed into the client's Windows command line.\n\n"
            				+ "Execution is asynchronous to client, no error on failure.\n\n"
            				+ "Example:     notepad.exe\n"
            				+ "Example:     taskkill /im notepad.exe\n\n", 
            				"Send Command to Clients", JOptionPane.PLAIN_MESSAGE, null, null, "");
            	
            	if (command != null && command.trim().length() > 0)
            	{
            		server.sendCommandToAllClients(command);
            	}
            }
        });
        actionsMenu.add(sendClientCommandMenuItem);
        
        viewClientScreenMenuItem = new JMenuItem("Take Client Screenshot", KeyEvent.VK_S);
        viewClientScreenMenuItem.addActionListener(new ActionListener() 
        {
            public void actionPerformed(ActionEvent e) 
            {
            	String client = (String)JOptionPane.showInputDialog(mainFrame, 
        				"Enter Address of Client\n\n"
        				+ "Will match if substring of address in client window\n\n"
        				+ "If multiple match, multiple will display\n\n"
        				+ "If Client screenshot already open, it will refresh\n\n",
        				"Take Screenshot of Client", JOptionPane.PLAIN_MESSAGE, null, null, "");
            	
            	if (client != null && client.trim().length() > 0)
            	{
            		server.sendScreenshotRequestToClient(client);
            	} 
            }
        });
        actionsMenu.add(viewClientScreenMenuItem);
        
        mainFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
	    mainFrame.setSize(900,600);
	    mainFrame.setJMenuBar(menuBar);
	    
	    GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 0.0;
		c.weighty = 0.0;
		mainFrame.add(statusPanel, c);
	    
	    c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 1;
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1.0;
		c.weighty = 1.0;
		JScrollPane mainTableScrollPane = new JScrollPane(mainTable);
		//ensures all space distributed evenly between this and bottom panel by GridBagLayout
		mainTableScrollPane.setPreferredSize(new Dimension(0,0)); 
	    mainFrame.add(mainTableScrollPane, c);
	    
	    c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 2;
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1.0;
		c.weighty = 1.0;
		bottomPanel.setPreferredSize(new Dimension(0,0));
		mainFrame.add(bottomPanel, c);
	    
		mainFrame.setVisible(true);
	    
	    mainFrame.addWindowListener(new WindowAdapter() 
	    {
	    	public void windowClosing(WindowEvent e) 
	    	{
    			int confirmed = JOptionPane.showConfirmDialog(mainFrame, "Shutdown Server: Are you sure?", "Shutdown Confirmation", JOptionPane.YES_NO_OPTION);
    			if (confirmed == JOptionPane.YES_OPTION)
    			{
    				server.shutDown();
    			}     
            }
	    });
	}
	
	public void handleFileDialogues()
	{
		// if we resumed a tournament, don't delete anything!
		if (resumedTournament)
		{
			setupTimer();
			return;
		}
		
		handleTournamentData();
		handleNoGamesFile();
		setupTimer();
	}
	
	public boolean handleTournamentResume()
	{
		int resumeTournament = JOptionPane.NO_OPTION;
		ResultsParser rp = new ResultsParser(ServerSettings.Instance().ResultsFile);
		
		if (rp.numResults() > 0)
		{
			resumeTournament = JOptionPane.showConfirmDialog(mainFrame, "Results found in " + ServerSettings.Instance().ResultsFile + ", resume tournament from games list in " + ServerSettings.Instance().GamesListFile + " ?" , "Resume Tournament Confirmation", JOptionPane.YES_NO_OPTION);
		}
			
		if (resumeTournament == JOptionPane.YES_OPTION)
		{
			resumedTournament = true;
		}
		
		return resumedTournament;
	}
	
	private void handleTournamentData()
	{
		try
		{
			int resClear = JOptionPane.NO_OPTION;
			if (ServerSettings.Instance().ClearResults.equalsIgnoreCase("ask"))
			{
				resClear = JOptionPane.showConfirmDialog(mainFrame, "Clear existing tournament data?\nThis will clear all existing results, replays and bot read/write folders.", "Clear Tournament Data", JOptionPane.YES_NO_OPTION);
			}
			else if (ServerSettings.Instance().ClearResults.equalsIgnoreCase("yes"))
			{
				resClear = JOptionPane.YES_OPTION;
			}
			else
			{
				resClear = JOptionPane.NO_OPTION;
			}
			 
			if (resClear == JOptionPane.YES_OPTION)
			{
				logText(getTimeStamp() + " Clearing Results File\n");
				File resultsFile = new File(ServerSettings.Instance().ResultsFile);
				if (resultsFile.exists())
				{
					resultsFile.delete();
				}
				
				logText(getTimeStamp() + " Clearing Bot Read / Write Directories\n");
    			for (Bot b : ServerSettings.Instance().BotVector)
    			{
    				String botRead = b.getServerDir() + "read/";
    				String botWrite = b.getServerDir() + "write/";
    				
    				FileUtils.CleanDirectory(new File(botRead)); 
    				FileUtils.CleanDirectory(new File(botWrite)); 
    			}
    			
    			logText(getTimeStamp() + " Clearing Replay Directory\n");
    			FileUtils.CleanDirectory(new File(ServerSettings.Instance().ServerReplayDir)); 
			}
		}
		catch (Exception e)
		{
			
		}
	}
	
	private void handleNoGamesFile()
	{
		// if the games list file doesn't exist
		if (!new File(ServerSettings.Instance().GamesListFile).exists())
		{
			int generate = JOptionPane.showConfirmDialog(mainFrame, "No games list was found.\nGenerate a new round robin games list file?", "Generate Games List?", JOptionPane.YES_NO_OPTION);
			
			if (generate == JOptionPane.YES_OPTION)
			{
				SpinnerNumberModel sModel = new SpinnerNumberModel(1, 1, 1000, 1);
				JSpinner spinner = new JSpinner(sModel);
	
				JOptionPane.showOptionDialog(mainFrame, spinner, "Enter Number of Rounds Per Map:", JOptionPane.PLAIN_MESSAGE, JOptionPane.QUESTION_MESSAGE, null, null, null);
				GameListGenerator.GenerateGames(Integer.parseInt("" + spinner.getValue()), ServerSettings.Instance().MapVector, ServerSettings.Instance().BotVector, ServerSettings.Instance().TournamentType);
			
				logText(getTimeStamp() + " " + "Generating Round Robin Tournament With " + spinner.getValue() + " Rounds.\n");
			}

			if (!new File(ServerSettings.Instance().GamesListFile).exists()) { System.err.println("ServerSettings: GamesListFile (" + ServerSettings.Instance().GamesListFile + ") does not exist"); System.exit(-1); }
		}
	}
	
	private void setupTimer()
	{
		startTime = Calendar.getInstance().getTime();
		Timer timer = new Timer(1000, new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				long diff = Calendar.getInstance().getTime().getTime() - startTime.getTime();
				long days = TimeUnit.MILLISECONDS.toDays(diff);
				long hours = TimeUnit.MILLISECONDS.toHours(diff) - TimeUnit.DAYS.toHours(days);
				long minutes = TimeUnit.MILLISECONDS.toMinutes(diff) - TimeUnit.DAYS.toMinutes(days) - TimeUnit.HOURS.toMinutes(hours);
				long seconds = TimeUnit.MILLISECONDS.toSeconds(diff) - TimeUnit.DAYS.toSeconds(days) - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.MINUTES.toSeconds(minutes);
				String time = days > 0 ? (days > 1 ? (days + " days, ") : (days + " day, ")) : "";
				//time += hours > 0 || days > 0 ? (hours != 1 ? (hours + " hours, ") : (hours + " hour, ")) : "";
				//time += minutes > 0 || hours > 0 || days > 0 ? (minutes != 1 ? (minutes + " minutes, ") : (" 1 minute, ")) : "";
				//time += seconds > 1 ? (seconds + " seconds") : seconds + " second";
				time += hours >= 10 ? hours + ":"  : "0" + hours + ":";
				time += minutes >= 10 ? minutes + ":"  : "0" + minutes + ":";
				time += seconds >= 10 ? seconds : "0" + seconds;
				
				uptime.setText(time);
			}
		});
		timer.start();
	}
	
	public static String getTimeStamp()
	{
		return new SimpleDateFormat("[MMM d, HH:mm:ss]").format(Calendar.getInstance().getTime());
	}
	
	public synchronized void UpdateClient(String name, String status, String num, String host, String join, String properties)
	{
		int row = GetClientRow(name);
		if (row != -1)
		{
			GetModel().setValueAt(status, row, 1);
			GetModel().setValueAt(name, row, 0);
			GetModel().setValueAt(num, row, 2);
			GetModel().setValueAt(properties, row, 8);
			
			if (!status.equals("READY") && !status.equals("SENDING"))
			{
				GetModel().setValueAt("", row, 3);
				GetModel().setValueAt("", row, 4);
				GetModel().setValueAt("", row, 5);
				GetModel().setValueAt("", row, 6);
				GetModel().setValueAt("", row, 7);
			}
			else
			{
				for (int i=3; i<columnNames.length; ++i)
				{
					GetModel().setValueAt(mainTable.getValueAt(row, i), row, i);
				}
			}
		}
		else
		{
			GetModel().addRow(new Object[]{name, status, num, host, join, "", "", "", properties});
			filterSelect.addItem(name);
		}
	}
	
	public synchronized void UpdateRunningStats(String client, String self, String enemy, String map, String FrameCount, String win)
	{
		int row = GetClientRow(client);
		
		if (row != -1)
		{
			GetModel().setValueAt(self, row, 3);
			GetModel().setValueAt(enemy, row, 4);
			GetModel().setValueAt(map, row, 5);
			GetModel().setValueAt(FrameCount, row, 6);
			GetModel().setValueAt(win, row, 7);
		}
	}
	
	public synchronized void updateServerStatus(int games, int completed) {
		progressBar.setMaximum(games);
		progressBar.setValue(completed);
		progressBar.setStringPainted(true);
		progressBar.setString(completed + " / " + games);
	}
	
	public synchronized int GetClientRow(String name)
	{
		for (int r=0; r<NumRows(); ++r)
		{
			String rowName = (String)(GetModel().getValueAt(r,0));
			if (rowName.equalsIgnoreCase(name))
			{
				return r;
			}
		}
		
		return -1;
	}
	
	public void RemoveClient(String name)
	{
		int row = GetClientRow(name);
		
		if (row != -1)
		{
			GetModel().removeRow(row);
		}
		for (int i = 0; i < filterSelect.getModel().getSize(); i++)
		{
			if (filterSelect.getItemAt(i).equalsIgnoreCase(name))
			{
				filterSelect.removeItemAt(i);
			}
		}
	}
	
	public synchronized void logText(String s)
	{
		log.add(s);
		if (filter.equalsIgnoreCase("all"))
		{
			bottomText.append(s);
			bottomText.setCaretPosition(bottomText.getDocument().getLength());
		}
		else if (filter.equalsIgnoreCase("server"))
		{
			Pattern pattern = Pattern.compile(".*[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}.*");
			Matcher matcher = pattern.matcher(s);
			if (!matcher.find() || s.toLowerCase().contains("server"))
			{
				bottomText.append(s);
				bottomText.setCaretPosition(bottomText.getDocument().getLength());
			}
		}
		else if (s.contains(filter))
		{
			bottomText.append(s);
			bottomText.setCaretPosition(bottomText.getDocument().getLength());
		}
	}
	
	public void filterLog()
	{
		StringBuilder filtered = new StringBuilder();
		
		if (filter.equalsIgnoreCase("all"))
		{
			for (int i = 0; i < log.size(); i++)
			{
				filtered.append(log.get(i));
			}
		}
		else if (filter.equalsIgnoreCase("server"))
		{
			//excludes all lines that have an ip address
			Pattern pattern = Pattern.compile(".*[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}.*");
			for (int i = 0; i < log.size(); i++)
			{
				Matcher matcher = pattern.matcher(log.get(i));
				if (!matcher.find() || log.get(i).toLowerCase().contains("server"))
				{
					filtered.append(log.get(i));
				}
			}
		}
		else
		{
			for (int i = 0; i < log.size(); i++)
			{
				if (log.get(i).contains(filter))
				{
					filtered.append(log.get(i));
				}
			}
		}
		
		bottomText.setText(filtered.toString());
	}
	
	public int NumRows()
	{
		return GetModel().getRowCount();
	}
	
	public int RowCount()
	{
		return GetModel().getColumnCount();
	}
	
	private DefaultTableModel GetModel()
	{
		return (DefaultTableModel)(mainTable.getModel());
	}
	
	class MyRenderer extends DefaultTableCellRenderer
	{
		private static final long serialVersionUID = -6642925623417572930L;

		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
		{
			Component cell = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			setBorder(noFocusBorder);
			
			//if row is selected, use default selection color
			if (isSelected)
			{
				return cell;
			}
			
			String status = (String)table.getValueAt(row, 1);
			
			if(status.equals("RUNNING"))
			{
				cell.setBackground(Color.green);
			}
			else if (status.equals("STARTING"))
			{
				cell.setBackground(Color.yellow);
			}
			else if (status.equals("READY"))
			{
				cell.setBackground(Color.white);
			}
			else if (status.equals("SENDING"))
			{
				cell.setBackground(Color.orange);
			}
			else 
			{
				//this shouldn't happen
				cell.setBackground(Color.red);
			}
			
			return cell;
		}
	}
	
	class MainTableModel extends DefaultTableModel
	{
		private static final long serialVersionUID = 8886359636823991784L;

		public MainTableModel(Object[][] data, String[] columnNames)
		{
			super(data, columnNames);
		}

		public boolean isCellEditable(int row, int column)
		{
			return false;
		}
	}
}
