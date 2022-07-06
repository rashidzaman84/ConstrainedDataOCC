package org.processmining.constraineddataocc.helper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.io.FilenameUtils;
import org.deckfour.xes.extension.XExtensionManager;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.factory.XFactoryBufferedImpl;
import org.deckfour.xes.in.XUniversalParser;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.out.XSerializer;
import org.deckfour.xes.out.XesXmlSerializer;

import cern.jet.random.AbstractContinousDistribution;
import cern.jet.random.Exponential;
import cern.jet.random.engine.DRand;

public class TimestampingEventData {

	private double mu;
	private double lambda;
	private Date startDate;
	private AbstractContinousDistribution casesInterArrivalDistribution;
	private AbstractContinousDistribution eventsInterDurationDistribution;

	public TimestampingEventData(int mu, int lambda) {

		this(synthesizeDate("01/03/2022 00:00:00"), mu, lambda);
	}
	
	public TimestampingEventData(String startDate, int mu, int lambda) {

		this(synthesizeDate(startDate), mu, lambda);
	}
	
	public TimestampingEventData(Date startDate, double mu, double lambda) {
		this.startDate = startDate;
		this.mu = mu;
		this.lambda = lambda;
		//this.casesInterArrivalDistribution = new Exponential(1.0 / getTimeInMiliseconds(dialog.getTBA_Label_1(), dialog.getTBATimeUnits()),	engine);  //new Exponential(1.0/3600000.0, new DRand()); //Exponential(Mean, RandomEngine arg1)
//		this.casesInterArrivalDistribution = new Exponential(1.0 / Math.round(mu * 1000.0 * 60 /* 60*/),	new DRand());
//		this.eventsInterDurationDistribution = new Exponential(1.0 / Math.round(lambda * 1000.0 * 60 * 60),	new DRand());
		this.casesInterArrivalDistribution = new Exponential(1/(mu* 1000.0 * 60),	new DRand(7765));  //MINUTES --> Math.round(input * 1000.0 * 60) // HOURS --> Math.round(input * 1000.0 * 60 * 60);
		this.eventsInterDurationDistribution = new Exponential(1/(lambda* 1000.0 * 60),	new DRand(9898));
	}

	private static Date synthesizeDate(String providedDate) {
		SimpleDateFormat parser = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		try {
			return parser.parse(providedDate);
		} catch (ParseException e) {
			System.out.println("Provided date is incorrect");
			return null;
		}
	}

	public XLog timeStamping(XLog log) {

		XFactory xesFactory = new XFactoryBufferedImpl();
		XExtensionManager xesExtensionManager = XExtensionManager.instance();

		long startTime = startDate.getTime();

		for (int i = 0; i < log.size(); i++) {
			//timeStampTrace(startTime);

			long eventTimeStamp = startTime;

			for(XEvent event : log.get(i)) {
				
				event.getAttributes().put("time:timestamp",
						xesFactory.createAttributeTimestamp("time:timestamp",eventTimeStamp,xesExtensionManager.getByName("Time")));
				eventTimeStamp = eventTimeStamp + eventsInterDurationDistribution.nextInt();
			}
			
			if(mu > 1 && lambda >1) {
				startTime = startTime + casesInterArrivalDistribution.nextInt();
			}else if(lambda==1) {
				startTime = eventTimeStamp;
			}
			
		}
		return log;		
	}
	
	public static void main(String[] args) throws IOException {
		
//		int[] mus = {0, 1000, 25, 50, 50};
//		int[] lambdas = {1000, 0, 50, 50, 25};
		
		int[] mus = {50, 50, 50};
		int[] lambdas = {10, 5, 2};
		
//		int mu = 50;
//		int lambda = 25;		
		String inputFiles = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/Process Models from Eric/Event Logs Repository/a12/";
		File inputFolder = new File(inputFiles); 
		for (File variant : inputFolder.listFiles()) { 
			if (variant.isDirectory() || !FilenameUtils.getBaseName(variant.getName()).endsWith("00")) {
				continue;
			}
			XLog log = null; 
			try {
				log = new XUniversalParser().parse(variant/*new File(inputFiles + "/" + variant)*/).iterator().next();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			for(int k=0; k<mus.length; k++) {
				TimestampingEventData timestampingEventData = new TimestampingEventData(mus[k], lambdas[k]);
				XLog log_ = timestampingEventData.timeStamping(log);
				SerializeLog(log_, "D:/Research Work/latest/Streams/Rashid Prefix Alignment/test/Parallelism Analysis/additional logs/" + FilenameUtils.getBaseName(variant.getName()) +  "_" + mus[k] + "_" + lambdas[k] + ".xes" );
			}
			
			
		}
	}
	
	public static void SerializeLog(XLog log, String outputFilePath) {

		//String outputFilePath = "D:/Research Work/latest/Streams/Rashid Prefix Alignment/test/timed logs/" + fileName + ".xes";
		File file = new File(outputFilePath);

		XSerializer serializer = new XesXmlSerializer();	

		try {			
			FileOutputStream fos = new FileOutputStream(file);
			serializer.serialize(log, fos);
			fos.close();
		} catch(Exception e) {
			e.printStackTrace();
		} 	

	}

}
