package be.dynatrace.apps.settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import org.json.JSONObject;

import be.dynatrace.api.client.ApiClient;
import be.dynatrace.api.env.v2.MonitoredEntities;
import be.dynatrace.api.env.v2.SettingsObjects;
import be.dynatrace.api.model.v2.SettingsObjectList;
import be.dynatrace.api.model.v2.SettingsSchemaDetail;
import be.dynatrace.api.model.v2.SettingsSchemaList;
import be.dynatrace.api.model.v2.SettingsSchemaSummary;

public class Settings {

	private static String workdir = "C:\\Data\\Dynatrace\\Development\\settings\\";
//	private static String from = "now-30d";
	private static Map<String,JSONObject> entitycache=new HashMap<String,JSONObject>();
	private static long lastseen=0l;
	private static String[] stripfields={};
	
	private static Properties initialize(String config) {
		Properties p=new Properties();
		try {
			p.load(new FileInputStream("config\\"+config+".properties"));
		} catch (Exception e) {
			System.err.println("Config file not found or invalid property file");
//			System.exit(-1);			
		}

		if (!(p.containsKey("url") && p.containsKey("token"))){
			System.err.println("Property file requires url and token ...");
			System.exit(-1);			
		}

		if (p.containsKey("workdir")) {
			workdir=p.getProperty("workdir")+"\\settings\\";
		}
//		if (p.containsKey("from")) {
//			from=p.getProperty("from");
//		}
		
		if (p.containsKey("lastseen")) {
			lastseen=Long.parseLong(p.getProperty("lastseen"));
		}

		if (p.containsKey("stripfields")) {
			
			StringTokenizer st=new StringTokenizer(p.getProperty("stripfields"), ",");
			
			int ntok=st.countTokens();
			stripfields=new String[ntok];
			for (int i=0;i<ntok;i++) {
				stripfields[i]=st.nextToken();
			}
			
			lastseen=Long.parseLong(p.getProperty("lastseen"));
		}
		
		return p;
	}

	private static void writeSchema(SettingsSchemaDetail ssd){
		// create folder
		try {
			//DEBUG
			//System.err.println("Creating folder hierarchy: "+workdir + "_schemas");
			File fe = new File(workdir + "_schemas");
			fe.mkdirs();
			FileWriter ew = new FileWriter(
				workdir + "_schemas\\" + normalize(ssd.getSchemaid()) + ".json");
			ssd.getRaw().write(ew, 2, 0);
			ew.close();
		} catch (Exception e) {
			System.err.println("Failed to write schema: "+ssd.getSchemaid());
			//e.printStackTrace(System.err);
		}		
	}
	
	public static void main(String[] args) {
		
		if (args.length!=1) {
			System.out.println("Usage: Settings <env>\n\tthis will load config\\<env>.properties");
			System.exit(-1);
		}
				
		Properties p=initialize(args[0]);

		ApiClient ac = new ApiClient(p);
		
		try {
			System.out.println("Getting schemas");

			SettingsSchemaList ssl = SettingsSchemaList.load(ac);
			Map<String, SettingsSchemaSummary> schemas = ssl.getItems();
			Map<String, SettingsSchemaDetail> schemadetails = new HashMap<String, SettingsSchemaDetail>();

			schemas.forEach((id, schema) -> {
				
				//DEBUG
				System.out.println("Loading schema and objects : "+id);
				
				// load detailed schema
				SettingsSchemaDetail ssd = schema.loadDetail(ac);
				schemadetails.put(id, ssd);
				writeSchema(ssd);
	
				// loadschemavalues	
				SettingsObjectList sol = SettingsObjectList.load(ac, id);

				// foreach -> writeSchemaValue
				if (sol.hasItems()) {

					sol.getItems().forEach((cfgid, config) -> {
						//DEBUG
						System.out.print("+");
						String scope="<unresolved>";
						boolean skip=false;
						try {
							JSONObject so=SettingsObjects.getSettingsObject(ac, cfgid);
							
							String ffscope=so.getString("scope");
							scope=normalize(ffscope);
							
							if (ffscope.matches("[A-Z_]+-.*")){
								String etype=ffscope.substring(0, ffscope.lastIndexOf("-"));

								String id2;
								// get detailed entity
								JSONObject entity;
								boolean first=false;
								if (entitycache.containsKey(ffscope)) {
									entity=entitycache.get(ffscope);
								} else {
									entity = MonitoredEntities.getEntity(ac, ffscope);
									first=true;
									entitycache.put(ffscope, entity);
								}
								
								if ((lastseen>0l) && (entity.has("error") || entity.getLong("lastSeenTms")<lastseen))
									skip=true;

								if (entity.has("displayName")) {
									switch(etype) {
										case "APPLICATION":
										case "CUSTOM_APPLICATION":
										case "HOST":
										case "HOST_GROUP":
										case "HTTP_CHECK":
										case "KUBERNETES_CLUSTER":	
										case "MOBILE_APPLICATION":	
										case "SYNTHETIC_TEST":
											id2=normalize(entity.getString("displayName"));
											break;
										case "PROCESS_GROUP":
										case "SERVICE":
											id2=normalize(entity.getString("displayName")+" - "+ffscope);
											break;
										default:
											id2=ffscope;
											break;
									}
								} else {
									id2=ffscope;									
								}

								if (first && !skip) {
									writeEntity(etype, id2, entity);
								}
										
								scope=etype+"\\"+id2;
							} else if (ffscope.startsWith("metric")) {
								scope="metric\\"+normalize(ffscope.substring(7));
							} else if (ffscope.startsWith("ua-screen")) {
								scope="ua-screen\\"+normalize(ffscope.substring(10));								
							}
							
							if (!skip)
								writeSchemaValues(workdir + scope,SettingsObjects.getSettingsObject(ac, cfgid),ssd.getRaw().getBoolean("multiObject"));
						} catch (Exception e) {
							System.err.println("ERROR while writing entity settings for: " + scope + " :: " + cfgid);
							// DEBUG e.printStackTrace(System.err);
						}
					});
					//DEBUG
					System.out.println("");
				}	
			});

		} catch (Exception e) {
			e.printStackTrace(System.err);
		}
		System.out.println("ENDING Settings export ...");
	}
	
	private static void writeSchemaValues(String base,JSONObject schema, boolean multi) {
		
		String foldername;
		String filename;
		
		if (multi) {
			foldername=base+"\\"+normalize(schema.getString("schemaId"));
			
			if (schema.getJSONObject("value").has("name"))
				filename=  base+"\\"+normalize(schema.getString("schemaId"))+"\\"+normalize(schema.getJSONObject("value").getString("name"))+".json";
			else if (schema.getJSONObject("value").has("displayName"))
				filename=  base+"\\"+normalize(schema.getString("schemaId"))+"\\"+normalize(schema.getJSONObject("value").getString("displayName"))+".json";
			else if (schema.getJSONObject("value").has("apiName"))
				filename=  base+"\\"+normalize(schema.getString("schemaId"))+"\\"+normalize(schema.getJSONObject("value").getString("apiName"))+".json";
			else if (schema.has("summary") && !schema.getString("summary").equals(""))
				filename=  base+"\\"+normalize(schema.getString("schemaId"))+"\\"+normalize(schema.getString("summary"))+".json";
			else 
				filename=  base+"\\"+normalize(schema.getString("schemaId"))+"\\"+normalize(schema.getString("objectId"))+".json";				
		} else {
			foldername=base;
			filename=  base+"\\"+normalize(schema.getString("schemaId"))+".json";
			
		}
		
		
		
		// cleanup schema to simplify comparison
		for (String rem : stripfields)
			schema.remove(rem);
			
		// create folder
		File fe = new File(foldername);
		fe.mkdirs();

		try {
			FileWriter cw = new FileWriter(filename);
			schema.write(cw, 2, 0);
			cw.close();
		} catch (Exception e) {
			System.err.println("ERROR while writing settings.["+filename+"]");
			e.printStackTrace(System.err);
		}
	}

	private static void writeEntity(String etype,String id,JSONObject entity) {
		// create folder
		File fe = new File(workdir + etype + "\\" + id);
		fe.mkdirs();

		try {
			FileWriter ew = new FileWriter(workdir + etype + "\\" + id + "\\" + id + ".json");
			entity.write(ew, 2, 0);
			ew.close();
		} catch (Exception e) {
			System.err.println("ERROR while writing entity.["+etype+"/"+id+"]");
			e.printStackTrace(System.err);
		}	
	}
	
	private static String normalize(String escapeme) {
		return escapeme.replaceAll(":","_")
				.replaceAll("<","_")
				.replaceAll(">","_")
				.replaceAll("\\*","_")				
				.replaceAll("/","_")			
				.replaceAll("\"","_")
				.replaceAll("\\?","_")
				.replaceAll("[ ]++$","");
				// .replaceAll("[ ]++\\","\\");
	}
}
