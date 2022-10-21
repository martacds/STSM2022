public class CandidateOntologies {

	public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException, IOException {

		// settings
		Settings set = Settings.getInstance();
		StopList.init(ResourceManager.getStopSet());
		// load ontologies
		Ontology src = OntologyReader.parseInputOntology(args[0]); // hp
		Ontology tgt = OntologyReader.parseInputOntology(args[1]); // other
		//  

		set.defaultConfig(src, tgt);
		// extenders
		ParenthesisExtender pe = new ParenthesisExtender(); 
		pe.extendLexicons(src); 
		pe.extendLexicons(tgt); 
		StopWordExtender se = new StopWordExtender(); 
		se.extendLexicons(src); 
		se.extendLexicons(tgt);

		// Lexical Matcher
		LexicalMatcher lm = new LexicalMatcher();
		Alignment alm = lm.match(src, tgt, EntityType.CLASS, 0.0);

		// remove lexical matches from lexicon
		Lexicon srcLex = src.getLexicon(EntityType.CLASS);
		Lexicon tgtLex = tgt.getLexicon(EntityType.CLASS);
		for(Mapping m : alm) {
			srcLex.removeURI(m.getEntity1());
			tgtLex.removeURI(m.getEntity2());
		}

		// Word Matcher
		double thresh = 0.2;
		Alignment awm = new Alignment(src,tgt);
		Set<String> languages = src.getLexicon(EntityType.CLASS).getLanguages();
		languages.retainAll(tgt.getLexicon(EntityType.CLASS).getLanguages());
		for(String l : languages) {
			WordMatcher wm = new WordMatcher(l);
			awm.addAll(wm.match(src, tgt, EntityType.CLASS, thresh));
		}
		//		AlignmentIOTSV.save(awm, "/home/marta/Documents/2022_STSM/work/test_WM.tsv");
		AlignmentIOOWL.save(awm, "/home/marta/Documents/2022_STSM/work/test_WM.owl");
		System.out.println("with threshold " + thresh + ", alignment has " + awm.size() + " possible mappings");

		// best match average
		int i = 0;
		HashMap<String,Double> bestMatch = new HashMap<String, Double>(); // source uri, best similarity score
		for(String s : awm.getSources()) {
			double sim = 0.0;
			for(Mapping m : awm.getSourceMappings(s)) {
				if(m.getSimilarity() > sim) {
					sim = m.getSimilarity();
				}
				else {
					continue;
				}
			}
			bestMatch.put(s, sim);
			i = i + 1;
		}

		double total = 0.0;
		for(String k : bestMatch.keySet()) {
			total = total + bestMatch.get(k);
		}
		System.out.println("best match average: " + (total/awm.getSources().size()));
		double finalScore = (total/awm.getSources().size());
		
		// how many times the ontology shows up in the logical definitions
		ReferenceMap rm = src.getReferenceMap();
		String o1 = StringUtils.substringBefore(args[0].split("/")[7], ".");
		String o2 = StringUtils.substringBefore(args[1].split("/")[7], ".");

		int j = 0;
		for(String ref : rm.getReferences()) {
			if(ref.contains("<")) {
				for(String component : ref.split(" ")) {
					component = component.replaceAll("<", "").replaceAll(">", "").replaceAll("ObjectSomeValuesFrom", "").replaceAll("ObjectIntersectionOf", "").replaceAll("\\(", "").replaceAll("\\)", "").replaceAll("ObjectUnionOf", "");
					if(component.toLowerCase().contains(o2 + "_")) {
						j = j + 1;
					}
				}
			}
		}
					
		// creates file for prints
		FileWriter fw = new FileWriter("/home/marta/Documents/2022_STSM/work/results_02.tsv", true);
		BufferedWriter bw = new BufferedWriter(fw);
		PrintWriter out = new PrintWriter(bw);
		out.println(o1 + "_" + o2 + "\t" + finalScore + "\t" + awm.size() + "\t" + j);
		out.close();


	}

	public static void createReference(Ontology src, Ontology tgt) throws FileNotFoundException, OWLOntologyCreationException, OWLOntologyStorageException {
		
		ReferenceMap rm = src.getReferenceMap();
		Alignment reference = new Alignment(src,tgt);

		for(String ref : rm.getReferences()) {
			if(ref.contains("<")) {
				for(String uri : rm.getEntities(ref)) {
					for(String component : ref.split(" ")) {
						component = component.replaceAll("<", "").replaceAll(">", "").replaceAll("ObjectSomeValuesFrom", "").replaceAll("ObjectIntersectionOf", "").replaceAll("\\(", "").replaceAll("\\)", "").replaceAll("ObjectUnionOf", "");

						if(component.contains("GO_")) { // check that entity is in target
							Mapping m = new Mapping(uri, component, 1, MappingRelation.EQUIVALENCE);
							reference.add(m); // create new mapping
						}
					}
				}
			}
		}
		System.out.println("mappings in reference: " + reference.size());
		AlignmentIOTSV.save(reference, "/home/marta/Documents/2022_STSM/work/reference.tsv");
		AlignmentIOOWL.save(reference, "/home/marta/Documents/2022_STSM/work/reference.owl");
	}
