package utility;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Vector;

public class FileUtils 
{
	private static Vector<String> lockFiles = new Vector<String>();
	private static boolean hasLockFiles = false;

	public static void writeToFile(String data, String filename, boolean append)
	{
		try
		{
			File file = new File(filename);
			if (!file.exists())
			{
				file.createNewFile();
			}

			FileWriter fw = new FileWriter(file.getAbsoluteFile(), append);
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write(data);
			bw.close();
			fw.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	// attempt to clean up any lock files if shutting down
	private static synchronized void setupLockClean()
	{
		if (!hasLockFiles)
		{
			hasLockFiles = true;
			Runtime.getRuntime().addShutdownHook(new Thread()
			{
			    public void run()
			    {
			    	for (String filename : lockFiles)
			    	{
			    		try
						{
							Files.delete(Paths.get(filename));
						}
			    		catch (IOException e)
						{
							e.printStackTrace();
						}
			    	}
			    }
			});
		}
	}
	
	/**
	 * Creates a lock file to ensure uninterrupted access to a resource. Behaves similarly to node package "lockfile".
	 * No retries or stale lock files.
	 * @param filename    the name of the lock file to create (not the resource - so use "somefile.txt.lock" to lock "somefile.txt")
	 * @throws Exception  if a file can't be locked an exception is thrown
	 */
	public static void lockFile(String filename) throws Exception
	{
		lockFile(filename, 0, 1, 0);
	}
	
	/**
	 * Creates a lock file to ensure uninterrupted access to a resource. Behaves similarly to node package "lockfile".
	 * @param filename       the name of the lock file to create (not the resource - so use "somefile.txt.lock" to lock "somefile.txt")
	 * @param retries        number of times to retry locking
	 * @param retryInterval  ms to wait (blocking) between retries
	 * @param stale          ms until a lock file is considered stale and can be deleted to obtain a new lock. If stale is 0 then lock files can't be stale.
	 * @throws Exception     if a file can't be locked an exception is thrown
	 */
	public static synchronized void lockFile(String filename, int retries, long retryInterval, long stale) throws Exception
	{
		setupLockClean();
		if (retryInterval < 1)
		{
			retryInterval = 1;
		}
		try
		{
			Files.createFile(Paths.get(filename));
			lockFiles.add(filename);
		}
		catch (FileAlreadyExistsException e)
		{
			// another process has a lock on the file
			File file = new File(filename);
			if (stale > 0 && System.currentTimeMillis() - file.lastModified() > stale)
			{
				// lock is stale, try to get stale lock once
				// ".STALE" here is chosen to match https://www.npmjs.com/package/lockfile
				// delete the file and try again
				try
				{
					lockFile(filename + ".STALE", 0, 1, 0);
					Files.deleteIfExists(Paths.get(filename));
					lockFile(filename, retries, retryInterval, stale);
					unlockFile(filename + ".STALE");
				}
				catch (Exception eStale)
				{
					if (retries > 0)
					{
						Thread.sleep(retryInterval);
						lockFile(filename, retries - 1, retryInterval, stale);
					}
					else
					{
						throw new Exception("Could not establish lock on file " + filename);
					}
				}
			}
			else if (retries > 0)
			{
				//sleep for waitInterval ms and try again
				Thread.sleep(retryInterval);
				lockFile(filename, retries - 1, retryInterval, stale);
			}
			else
			{
				throw new Exception("Could not establish lock on file " + filename);
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	public static synchronized void unlockFile(String filename) throws IOException
	{
		for (int i = 0; i < lockFiles.size(); i++)
		{
			if (lockFiles.get(i).equals(filename))
			{
				lockFiles.remove(i);
				break;
			}
		}
		Files.delete(Paths.get(filename));
	}
	
	private static void DeleteRecursive(File f) 
	{
		try
		{
			if (f.isDirectory()) 
			{
				for (File c : f.listFiles())
				{
					DeleteRecursive(c);
				}
			}
			
			f.delete();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void CleanDirectory(File f) 
	{
		try
		{
			for (File c : f.listFiles())
			{
				DeleteRecursive(c);
			}
		}
		catch (Exception e)
		{
			
		}
	}
	
	public static void CreateDirectory(String dir)
	{
		File f = new File(dir); 
		if (!f.exists())
		{
			f.mkdirs();
		}
	}
	
	public static void DeleteDirectory(File f) 
	{
		System.out.println("Deleting directory:" + f.getAbsolutePath());
		DeleteRecursive(f);
	}
	
	public static void DeleteFile(File f)
	{
		DeleteRecursive(f);
	}
	
	public static void CopyFile(File source, File dest)
	{
		try
		{
			copy(new FileInputStream(source), new FileOutputStream(dest));
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void CopyDirectory(String source, String dest)
	{
		try
		{
			File sourceDir = new File(source);
			File destDir = new File(dest);	
			destDir.mkdirs();
			
			Path destPath = destDir.toPath();
			for (File sourceFile : sourceDir.listFiles()) 
			{
			    Path sourcePath = sourceFile.toPath();
			    File newFile = new File(sourcePath.toString());
			    
			    if (newFile.isDirectory())
			    {
			    	newFile.mkdirs();
			    	CopyDirectory(sourceFile.toString(), destPath.resolve(sourcePath.getFileName()).toString());
			    }
			    else
			    {
			    	Files.copy(sourcePath, destPath.resolve(sourcePath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
			    }
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void CopyFilesInDirectory(File source, File dest)
	{
		try
		{
			copy(new FileInputStream(source), new FileOutputStream(dest));
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private static void copy(InputStream in, OutputStream out) 
	{
		try
		{
			byte[] buffer = new byte[1024];
			while (true) 
			{
				int readCount = in.read(buffer);
				if (readCount < 0) 
				{
					break;
				}
				out.write(buffer, 0, readCount);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
	}
}
