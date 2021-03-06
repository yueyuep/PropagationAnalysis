package com.vmusco.smf.testing;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.vmusco.smf.analysis.MutantIfos;
import com.vmusco.smf.analysis.MutationStatistics;
import com.vmusco.smf.analysis.ProcessStatistics;
import com.vmusco.smf.analysis.persistence.MutantInfoXMLPersisitence;
import com.vmusco.smf.analysis.persistence.XMLPersistence;
import com.vmusco.smf.exceptions.MutantHangsException;
import com.vmusco.smf.exceptions.MutationNotRunException;
import com.vmusco.smf.exceptions.PersistenceException;
import com.vmusco.smf.exceptions.TestingException;

/**
 * Tools used for testing executions
 * @author Vincenzo Musco - http://www.vmusco.com
 */
public abstract class TestingFunctions {

	public static List<String> getViableCollection(MutationStatistics ms){
		ArrayList<String> al = new ArrayList<String>();
		for(String mut : ms.listMutants()){
			MutantIfos ifos = ms.getMutationStats(mut);

			if(!ifos.isViable()){
				continue;
			}

			al.add(mut);
		}

		return al;
	}

	public static List<String> getUnfinishedCollection(MutationStatistics ms, boolean shuffle){
		ArrayList<String> al = new ArrayList<String>();
		for(String mut : getViableCollection(ms)){
			//MutantIfos ifos = ms.getMutationStats(mut);
			File ff = new File(ms.getMutantFileResolved(mut));

			if(!ff.exists()){
				al.add(mut);
			}else if(ff.length() == 0){
				try{
					FileOutputStream fos = new FileOutputStream(ff);

					FileLock lock = fos.getChannel().tryLock();
					if(lock != null){
						lock.release();
						fos.close();
						ff.delete();
						al.add(mut);
					}
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(-1);
				}
			}
		}

		if(shuffle)
			Collections.shuffle(al);

		return al;
	}

	public static int processMutants(MutationStatistics ms, List<String> mutantIds, int nbdone, int nbmax, TestingNotification tn, boolean onlyKilled, boolean skipHanging) throws PersistenceException{
		int nbproc = nbdone;

		while(mutantIds.size() > 0){
			String mut = mutantIds.remove(0); 

			if(tn != null)	tn.mutantStarted(mut);
			MutantIfos ifos = ms.getMutationStats(mut);

			if(!ifos.isViable()){
				continue;
			}

			File ff = new File(ms.getMutantFileResolved(mut));

			if(!ff.exists() || ff.length() == 0){
				
				
				try{
					FileOutputStream fos = new FileOutputStream(ff);;
					FileLock lock = fos.getChannel().tryLock();
					try{
						if(lock != null){
							ProcessStatistics ps = ms.getRelatedProcessStatisticsObject();
							ifos.setExecutedTestsResults(Testing.runTestCases(ps.getProjectIn(true), ms.getRunningClassPath(mut), ms.getTestClasses(), ps.getTestTimeOut(), tn, ps.getAlternativeJre(), skipHanging));
	
							if(tn != null)	tn.mutantPersisting(mut);
	
							try{
								MutantInfoXMLPersisitence pers = new MutantInfoXMLPersisitence(ifos, ff);
								pers.setFileLock(fos);
								XMLPersistence.save(pers);
							}catch(PersistenceException e){
								if(e.getUnderException() instanceof MutationNotRunException){
									// Should not occurs here !!!
									System.err.print("After a generation unable to persis ?! Core error !");
									System.exit(1);
								}else{
									if(tn != null)	tn.mutantSkippedDueToException(mut);
								}
							}
	
							lock.release();
							fos.close();
	
							if(tn != null)	tn.mutantEnded(mut);
	
							nbproc++;
						}else{
							if(tn != null)	tn.mutantLocked();
							nbproc++;
						}
					} catch (TestingException e) {
						if(tn != null)	tn.mutantException(e);
					} catch(MutantHangsException e){
						if(tn != null) tn.mutantHangs(mut);
						lock.release();
						fos.close();
					}finally{
						// GC the results once saved...
						ifos.setExecutedTestsResults(null);
					}
				}catch(IOException e) {
					e.printStackTrace();
					System.exit(1);
				}
			}else{
				if(tn != null)	tn.mutantAlreadyDone();
			}

			if(nbproc >= nbmax){
				return nbproc;
			}
		}

		return nbproc;
	}

	public static int processMutants(MutationStatistics ms, List<String> mutantIds, int nbmax, TestingNotification tn) throws PersistenceException{
		return processMutants(ms, mutantIds, 0, nbmax, tn, false, false);
	}
}
