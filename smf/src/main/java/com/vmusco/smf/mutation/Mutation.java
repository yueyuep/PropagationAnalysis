package com.vmusco.smf.mutation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import spoon.compiler.SpoonCompiler;
import spoon.processing.Processor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtTypeMember;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import spoon.support.JavaOutputProcessor;
import spoon.support.StandardEnvironment;
import spoon.support.compiler.jdt.JDTBasedSpoonCompiler;
import spoon.support.reflect.declaration.CtElementImpl;

import com.vmusco.smf.analysis.MutantIfos;
import com.vmusco.smf.analysis.MutationStatistics;
import com.vmusco.smf.analysis.ProcessStatistics;
import com.vmusco.smf.compilation.ClassFileUtil;
import com.vmusco.smf.compilation.compilers.JavaxCompilation;
import com.vmusco.smf.exceptions.BadObjectTypeException;
import com.vmusco.smf.exceptions.HashClashException;
import com.vmusco.smf.exceptions.NotValidMutationException;
import com.vmusco.smf.exceptions.PersistenceException;
import com.vmusco.smf.utils.SafeInterruption;
import com.vmusco.smf.utils.NewReportedStandardEnvironment;
import com.vmusco.smf.utils.SpoonHelpers;

/**
 * This class contains tools for performing mutant generation
 * @author Vincenzo Musco - http://www.vmusco.com
 */
public final class Mutation {
	private static final Logger logger = LogManager.getFormatterLogger(Mutation.class.getSimpleName());
	private static final String MUTANT_FILE_PREFIX = "mutant_";

	private Mutation() {}

	public static MutationStatistics createMutationElement(ProcessStatistics ps, SmfMutationOperator<?> mutator) throws InstantiationException, IllegalAccessException, ClassNotFoundException{
		return createMutationElement(ps, mutator, null, null);
	}

	public static MutationStatistics createMutationElement(ProcessStatistics ps, SmfMutationOperator<?> mutator, String mutationid, String[] classToMutate) throws InstantiationException, IllegalAccessException, ClassNotFoundException{
		MutationStatistics ms = new MutationStatistics(ps, mutator);

		if(classToMutate != null){
			ms.setClassToMutate(classToMutate);
		}

		if(mutationid != null){
			ms.setMutationName(mutationid);
		}

		return ms;
	}

	public static void createMutants(ProcessStatistics ps, MutationStatistics ms, MutationCreationListener mcl, boolean reset) throws PersistenceException, BadObjectTypeException {
		createMutants(ps, ms, mcl, reset, 0);
	}


	public static void createMutants(ProcessStatistics ps, MutationStatistics ms, MutationCreationListener mcl, boolean reset, int safepersist) throws PersistenceException, BadObjectTypeException {
		createMutants(ps, ms, mcl, reset, -1, safepersist, null);
	}

	/**
	 * Extract all elements candidated to be mutated for a project and a mutation
	 * @param ps
	 * @param ms
	 * @param factory
	 * @return
	 * @throws BadObjectTypeException if the mutation operator declared in ms is NOT intended for direct mutation using SMF framework (ie. imported mutation operator)
	 */
	public static CtElement[] getMutations(ProcessStatistics ps, MutationStatistics ms, Factory factory) throws BadObjectTypeException{
		String[] mutateFrom = ms.getClassToMutate(true);
		if(mutateFrom == null || mutateFrom.length <= 0){
			if(!ps.isInstrumented()){
				mutateFrom = ps.getSrcToCompile(true);
			}else{
				mutateFrom = new String[ps.getSrcToCompile(false).length];
				
				int i=0;
				for(String s : ps.getSrcToCompile(false)){
					mutateFrom[i++] = ps.getNotInstrumentedFolder().getAbsolutePath()+"/"+s;
				}
			}
		}

		String[] cp;
		int i = 0;

		if(ps.getRunningClassPath() != null){
			cp = new String[ps.getRunningClassPath().length + 1];

			for(String cpe : ps.getRunningClassPath()){
				cp[i++] = cpe;
			}
		}else{
			cp = new String[1];
		}

		cp[i] = ps.getProjectOut(true);

		if(ms.getMutationOperator() instanceof SmfMutationOperator){
			return getMutations(mutateFrom,
					cp,
					(SmfMutationOperator<?>) ms.getMutationOperator(),
					factory);
		}else{
			throw new BadObjectTypeException("The mutation operator is NOT intended for doing concrete mutation with SMF mutation framework !");
		}
		

	}

	/**
	 * Extract all elements candidates to be mutated for a set of files according to a mutation operator
	 * @param mutateFrom list of sources files to mutate
	 * @param classpath class path to use
	 * @param mutationClassName the full name of the mutation operator to apply
	 * @param factory the spoon factory to use for mutation
	 * @return a list of CtElement to mutate
	 */
	public static CtElement[] getMutations(String[] mutateFrom, String[] cp, SmfMutationOperator<?> mutop, Factory factory){
		SpoonCompiler compiler = new JDTBasedSpoonCompiler(factory);

		for(String srcitem : mutateFrom){
			compiler.addInputSource(new File(srcitem));
		}

		//Updating classpath
		compiler.setSourceClasspath(cp);

		// Build (in memory)
		compiler.build();

		// Obtain list of element to mutate
		List<Processor<?>> arg0 = new ArrayList<>();
		arg0.add(mutop);
		
		compiler.process(arg0);

		return MutationGateway.getMutationCandidates();
	}

	/**
	 * This method generate source code for a mutation in a temporary folder. 
	 * The mutation changes target obtained using to on e with m
	 * It search the hash in hashes if so, the process is aborted, else, the source code is built (if possible) and then
	 * the temporary folder is returned to caller for processing files.
	 * @param e element to change
	 * @param m change to apply
	 * @param to effective target obtained from e
	 * @param factory
	 * @param hashes hash of generations (check if already generated -- null to disable the check)
	 * @param testingClassPath
	 * @return null if element cannot be mutated or if aborted, else, the temporary directory containing a folder "src" with the mutated sources and a "bytecode" file or folder if built has succeeded or not. 
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 * @throws HashClashException
	 * @throws NotValidMutationException
	 */
	public static MutantIfos probeMutant(CtElement e, CtElement m, TargetObtainer to, Factory factory, Set<String> hashes, String[] testingClassPath, int compliance) throws IOException, NoSuchAlgorithmException, HashClashException, NotValidMutationException{
		CtClass<?> theClass = findAssociatedClass(e);

		CtElementImpl toReplace = (CtElementImpl) to.determineTarget(e);
		CtElementImpl replaceWith = (CtElementImpl) m;
		CtExecutable<?> exec = toReplace.getParent(CtExecutable.class);

		MutantIfos ifos = new MutantIfos();
		ifos.setMutationIn(Mutation.getMethodFullSignatureForParent(toReplace));
		ifos.setSourceReference(MutationStatistics.generateSourceReferenceForMutation(toReplace));

		if(ifos.getMutationIn() == null){
			throw new NotValidMutationException();
		}

		ifos.setMutationFrom(toReplace.toString());
		ifos.setMutationTo(replaceWith.toString());
		
		replaceWith.setParent(toReplace.getParent());
		if(!replace(toReplace, replaceWith)){
			return null;
		}
		
		File tmpf = File.createTempFile("mutation_probe", null);
		ifos.setGenerationDirectory(tmpf);

		if(tmpf.delete()){
			tmpf.mkdirs();
			tmpf.deleteOnExit();
			File src = new File(tmpf, "src");
			src.mkdirs();
			persistMutantClass(theClass, src.getAbsolutePath(), factory);
		}

		ifos.setHash(convertByteHashToString(getHashForMutationSource(tmpf.getAbsolutePath())));

		if(hashes != null && hashes.contains(ifos.getHash())){
			// Revert before interrupting
			if(!replace(replaceWith, toReplace)){
				return null;
			}
			throw new HashClashException();
		}

		// Compile it...
		JavaxCompilation c = new JavaxCompilation();
		DiagnosticCollector<JavaFileObject> diagnostics = c.getDiagnosticCollector(); 
		Map<String, byte[]> built = c.buildInMemory(theClass.getQualifiedName(), SpoonHelpers.generateAssociatedClassContent(theClass), testingClassPath, compliance);

		File bc = new File(tmpf, "bytecode");
		if(built != null){
			ifos.setViable(true);

			//TODO: theClass.isTopLevel() ==> Should be taken into consideration !?
			bc.mkdirs();
			persistBytecodes(built, bc.getAbsolutePath());
		}else{
			ifos.setViable(false);
			if(ifos.getMutationIn() == null)
				ifos.setMutationIn("?");

			FileOutputStream fos = new FileOutputStream(bc.getAbsolutePath());

			for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
				if(diagnostic.getCode() != null){
					fos.write(diagnostic.getCode().getBytes());
					fos.write("\n".getBytes());
				}
				if(diagnostic.getKind() != null){
					fos.write(diagnostic.getKind().toString().getBytes());
					fos.write("\n".getBytes());
				}
				fos.write(Long.toString(diagnostic.getPosition()).getBytes());
				fos.write("\n".getBytes());
				fos.write(Long.toString(diagnostic.getStartPosition()).getBytes());
				fos.write("\n".getBytes());
				fos.write(Long.toString(diagnostic.getEndPosition()).getBytes());
				fos.write("\n".getBytes());
				if(diagnostic.getSource() != null){
					fos.write(diagnostic.getSource().toString().getBytes());
					fos.write("\n".getBytes());
				}
				if(diagnostic.getMessage(null) != null){
					fos.write(diagnostic.getMessage(null).getBytes());
					fos.write("\n".getBytes());
				}
				fos.write("=====\n".getBytes());
			}

			fos.close();
		}

		if(!replace(replaceWith, toReplace)){
			return null;
		}
		return ifos; 
	}

	private static boolean replace(CtElement toReplace, CtElement replaceWith) {
		if(toReplace instanceof CtExpression && replaceWith instanceof CtExpression){
			((CtExpression)toReplace).replace((CtExpression)replaceWith);
		}else if(toReplace instanceof CtStatement && replaceWith instanceof CtStatement){
			((CtStatement)toReplace).replace((CtStatement)replaceWith);
		}else{
			logger.warn("Incompatible types for mutation: %s and %s", toReplace.getClass().getSimpleName(), replaceWith.getClass().getSimpleName());
			return false;
		}
		
		return true;
	}

	/**
	 * 
	 * @param ps
	 * @param ms
	 * @param mcl
	 * @param reset
	 * @param nb
	 * @param safepersistFold
	 * @throws PersistenceException
	 * @throws BadObjectTypeException if the mutation operator declared in ms is NOT intended for direct mutation using SMF framework (ie. imported mutation operator)
	 */
	public static void createMutants(ProcessStatistics ps, MutationStatistics ms, final MutationCreationListener mcl, boolean reset, int nb, int safepersist, SafeInterruption si) throws PersistenceException, BadObjectTypeException{
		try{
			Factory factory = SpoonHelpers.obtainFactory();

			long t1 = System.currentTimeMillis();
			int mutantcounter = 0;

			// Prepare generation workspace (eventually clear it)
			File f = new File(ms.getSourceMutationResolved());
			if(reset && f.exists()){
				System.out.println("Mutant sources folder exists... Erasing...");
				FileUtils.deleteDirectory(f);
				System.out.println("Succeded: "+(f.exists()?"False":"True"));
			}
			if(!f.exists())
				f.mkdirs();

			f = new File(ms.getBytecodeMutationResolved());
			if(reset && f.exists()){
				System.out.println("Mutant bytecode folder exists... Erasing...");
				FileUtils.deleteDirectory(f);
				System.out.println("Succeded: "+(f.exists()?"False":"True"));
			}
			if(!f.exists())
				f.mkdirs();

			if(reset){
				ms.clearMutations();
			}else{
				for(String s : ms.listMutants()){
					int numb = Integer.valueOf(s.substring(MUTANT_FILE_PREFIX.length()));
					if(numb >= mutantcounter){
						mutantcounter = numb+1;
					}
				}

				System.out.println("Syncing generation folder");

				File syncf = new File(ms.getBytecodeMutationResolved());
				for(String s : syncf.list()){
					String ss = s;
					if(s.endsWith(".debug.txt")){
						ss = s.substring(0, s.length()-".debug.txt".length());
					}

					if(!ms.isMutantDefined(ss)){
						File ssyncf = new File(syncf, s);

						if(ssyncf.isDirectory()){
							FileUtils.deleteDirectory(ssyncf);
						}else{
							ssyncf.delete();
						}
						System.out.println("Dropped "+s);
					}
				}

				System.out.println("Continue generation @ mutant "+mutantcounter);
			}

			List<Object[]> mutations = new ArrayList<Object[]>();

			for(CtElement e : getMutations(ps, ms, factory)){
				HashMap<CtElement, TargetObtainer> mutatedEntriesWithTargets = obtainsMutationCandidates((SmfMutationOperator<?>) ms.getMutationOperator(), e, factory);

				if(mutatedEntriesWithTargets == null)
					continue;

				Iterator<CtElement> iterator = mutatedEntriesWithTargets.keySet().iterator();

				while(iterator.hasNext()){
					CtElement m = iterator.next();
					TargetObtainer to = mutatedEntriesWithTargets.get(m);

					Object[] o = new Object[]{ e, m, to };
					mutations.add(o);
				}
			}

			Collections.shuffle(mutations);
			final Set<String> mutHashs = new HashSet<String>();

			for(String m : ms.listMutants()){
				MutantIfos mi = (MutantIfos) ms.getMutationStats(m);

				if(mi.getHash() == null){
					String outp = ms.getSourceMutationResolved() + File.separator + m;
					System.out.println("Fixing hash for "+outp);
					try{
						mi.setHash(convertByteHashToString(getHashForMutationSource(outp)));
					}catch(Exception ex){
						System.out.println("Unable to extract hash for "+m);
					}
				}

				mutHashs.add(mi.getHash());
			}

			int validmutants = 0;
			int nbmutants = 0;
			int droppedmutants = 0;
			int hashclashcpt = 0;
			int fnb = (nb<0 || nb > mutations.size())?mutations.size():nb;

			if(mcl != null) mcl.preparationDone(mutations.size(), fnb);

			while(mutations.size()>0 && validmutants<fnb){
				if(si != null){
					if(si.isInterruptDemanded()){
						break;
					}
				}
				
				final Object[] o = mutations.remove(0);

				try{
					if(mcl != null) mcl.newMutationProposal((CtElement)o[0], (CtElementImpl)o[1]);
					
					MutantIfos tmpmi = probeMutant((CtElement) o[0],
							(CtElementImpl) o[1],
							(TargetObtainer) o[2],
							factory,
							mutHashs,
							ps.getTestingClasspath(),
							ps.getComplianceLevel());

					if(tmpmi != null){
						String mutationid = MUTANT_FILE_PREFIX+mutantcounter++;
						String outp = ms.resolveName(ps.getMutantsOut());

						FileUtils.moveDirectory(new File(tmpmi.getGenerationDirectory(), "src"), new File(outp, mutationid));

						String boutp = ms.resolveName(ps.getMutantsBytecodeOut());
						if(tmpmi.isViable()){
							FileUtils.moveDirectory(new File(tmpmi.getGenerationDirectory(), "bytecode"), new File(boutp, mutationid));
							validmutants++;
							if(mcl != null) mcl.viableMutant((CtElement)o[0], (CtElementImpl)o[1]);
						}else{
							FileUtils.moveFile(new File(tmpmi.getGenerationDirectory(), "bytecode"), new File(boutp, mutationid+".debug.txt"));
							if(tmpmi.getMutationIn() == null){
								tmpmi.setMutationIn("?");
							}

							droppedmutants++;
							if(mcl != null) mcl.unviableMutant((CtElement)o[0], (CtElementImpl)o[1]);
						}

						mutHashs.add(tmpmi.getHash());
						ms.setMutationStats(mutationid, tmpmi);

						nbmutants++;
						if(safepersist > 0 && nbmutants%safepersist == 0){
							ms.saveMutants();
						}
					}
				}catch(HashClashException e){
					if(mcl != null) mcl.alreadyProcessedMutant((CtElement)o[0], (CtElementImpl)o[1]);
					hashclashcpt++;
				}catch(NotValidMutationException e){
					// Sillent skip
					//TODO: log ?
				}catch(Exception ex){
					System.out.println("Should not occurs !!!");
					ex.printStackTrace();
					System.exit(1);
				}

				if(mcl != null) mcl.endingMutationCheck(validmutants, droppedmutants, (CtElement)o[0]);
			}

			System.out.println(hashclashcpt);

			long t2 = System.currentTimeMillis();
			ms.setMutantsGenerationTime(t2-t1);

			if(mcl != null) mcl.mutationSummary(validmutants, droppedmutants+hashclashcpt, ms.getMutantsGenerationTime());
		}catch(IOException e){
			throw new PersistenceException(e);
		}
	}

	/**
	 * Produce a list of mutation replacement for an element according a mutation operator
	 * @param mo the mutation operator considered
	 * @param e the element to mutate
	 * @param factory the spoon factory element used to do mutation
	 * @return a list of mutation candidates for an element according a mutation operator
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static HashMap<CtElement, TargetObtainer> obtainsMutationCandidates(SmfMutationOperator mo, CtElement e, Factory factory) {
		CtClass<?> theClass = findAssociatedClass(e);

		if(theClass == null){
			logger.warn("Unable to find a parent class for the element %s. The item is skipped", e.getSignature());
			return null;
		}

		try{
			return mo.getMutatedEntriesWithTarget(e, factory);
		}catch(ClassCastException ex){
			ex.printStackTrace();
			return null;
		}
	}

	public static byte[] getHashForMutationSource(String mutant_roots) throws IOException, NoSuchAlgorithmException{
		File mutant_root = new File(mutant_roots);

		if(mutant_root.isDirectory()){
			Iterator<File> iterateFiles = FileUtils.iterateFiles(mutant_root, new String[]{"java"}, true);
			List<String> as = new ArrayList<String>();

			while(iterateFiles.hasNext()){
				File s = iterateFiles.next();
				as.add(s.getAbsolutePath());
			}

			Collections.sort(as);
			MessageDigest md = MessageDigest.getInstance("MD5");

			for(String ss : as){
				File s = new File(ss);
				String pt = s.getAbsolutePath().substring(mutant_root.getAbsolutePath().length());
				while(pt.charAt(0) == File.separatorChar)
					pt = pt.substring(1);

				md.update(pt.getBytes());
				md.update(FileUtils.readFileToByteArray(s));
			}

			byte[] re = md.digest();
			md.reset();
			return re;
		}

		return null;
	}

	public static String convertByteHashToString(byte[] digest){
		BigInteger bigInt = new BigInteger(1,digest);
		String hashtext = bigInt.toString(16);
		while(hashtext.length() < 32 ){
			hashtext = "0"+hashtext;
		}
		return hashtext;
	}

	private static String getMethodFullSignatureForParent(CtElement e){
		CtElement searchSignature = e;
		while(!(searchSignature instanceof CtMethod) && !(searchSignature instanceof CtConstructor) && searchSignature != null){
			searchSignature = searchSignature.getParent();
		}

		if(searchSignature == null){
			return null;
		}

		return SpoonHelpers.resolveName((CtTypeMember)searchSignature);
	}

	public static void persistMutantClass(CtClass<?> aClass, String outputPath, Factory f){
		StandardEnvironment env = new NewReportedStandardEnvironment();
		JavaOutputProcessor fileOutput = new JavaOutputProcessor(new File(outputPath), new DefaultJavaPrettyPrinter(env));
		fileOutput.setFactory(f);

		//SourcePosition sp = aClass.getPosition();
		aClass.setPosition(null);
		fileOutput.getCreatedFiles().clear();
		fileOutput.createJavaFile(aClass);
	}

	public static void persistBytecodes(Map<String, byte[]> bytecodes, String outfolder) throws IOException{
		for (String compiledClassName : bytecodes.keySet()){
			String fileName = new String(compiledClassName).replace('.', File.separatorChar) + ".class";
			byte[] compiledClass = bytecodes.get(compiledClassName);
			ClassFileUtil.writeToDisk(true, outfolder, fileName, compiledClass);
		}
	}

	public static CtClass<?> findAssociatedClass(CtElement e){
		CtElement c = e;

		while(c != null && (!(c instanceof CtClass) || (c instanceof CtClass && !((CtClass<?>)c).isTopLevel()))){
			c = c.getParent();
		}

		return (CtClass<?>)c;
	}
}
