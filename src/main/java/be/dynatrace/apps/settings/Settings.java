package be.dynatrace.apps.settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;

import org.json.JSONObject;


import be.dynatrace.api.client.ApiClient;
import be.dynatrace.api.client.ApiUtil;
import be.dynatrace.api.env.v2.MonitoredEntities;
import be.dynatrace.api.env.v2.SettingsObjects;
//import be.dynatrace.api.env.v2.SettingsSchemas;
import be.dynatrace.api.model.v2.EntityList;
import be.dynatrace.api.model.v2.SettingsObjectList;
import be.dynatrace.api.model.v2.SettingsObjectSummary;
//import be.dynatrace.api.client.ApiUtil;
//import be.dynatrace.api.model.v2.SettingsObjectList;
import be.dynatrace.api.model.v2.SettingsSchemaDetail;
import be.dynatrace.api.model.v2.SettingsSchemaList;
import be.dynatrace.api.model.v2.SettingsSchemaSummary;

public class Settings {

	private static String workdir = "C:\\Data\\Dynatrace\\Development\\MonacoV2";

	public static void main(String[] args) {
		
		if (args.length!=1) {
			System.out.println("Usage: Settings <env>\n\tthis will load <env>.properties");
			System.exit(-1);
		}
				
		Properties p=new Properties();
		try {
			p.load(new FileInputStream("config\\"+args[0]+".properties"));
		} catch (Exception e) {
			System.err.println("Config file not found or invalid property file");
//			System.exit(-1);			
		}

		if (!(p.containsKey("url") && p.containsKey("token"))){
			System.err.println("Property file requires url and token ...");
			System.exit(-1);			
		}

		ApiClient ac = new ApiClient(p);
		if (p.containsKey("workdir")) {
			workdir=p.getProperty("workdir");
		}
		
		try {
			System.out.println("Getting schemas");

			SettingsSchemaList ssl = SettingsSchemaList.load(ac);
			Map<String, SettingsSchemaSummary> schemas = ssl.getItems();
			Map<String, SettingsSchemaDetail> schemadetails = new HashMap<String, SettingsSchemaDetail>();

			Map<String, Vector<String>> scopedsettings = new HashMap<String, Vector<String>>();

			schemas.forEach((id, schema) -> {

				// load detailed schema
				SettingsSchemaDetail ssd = schema.loadDetail(ac);
				schemadetails.put(id, ssd);

				// create folder
				try {
					File fe = new File(workdir + "\\_schemas");
					fe.mkdirs();
					FileWriter ew = new FileWriter(
						workdir + "\\_schemas\\" + schema.getSchemaid().replaceAll(":","_") + ".json");
					ssd.getRaw().write(ew, 2, 0);
					ew.close();
				} catch (Exception e) {
					System.err.println("Failed to write schema: "+schema.getSchemaid());
				}
				
				for (String scope : ssd.getScopes()) {
					Vector<String> scopedlist = scopedsettings.get(scope);

					if (scopedlist == null) {
						scopedlist = new Vector<String>();
						scopedsettings.put(scope, scopedlist);
					}

					scopedlist.add(ssd.getSchemaid());
				}

			});

			scopedsettings.forEach((scope, settings) -> {
				System.out.println(scope);
				settings.forEach((schema) -> {
					System.out.println("  " + schema);
				});

//				File fs=new File(workdir+"\\"+scope);
//				fs.mkdirs();
				// TODO : create subfolder 'scope'
				// lookup all 'scope' objects from default period (or longer ?)
				// 'environment' - no lookup

				// foreach scoped object
				// lookup all configs with applicable schemas
				// if configs found
				// create subfolder scope/object
				// write object
				// write configs

				String[] sschemas = ApiUtil.vectorToArray(settings);
				boolean isEntity=scope.matches("[A-Z_]+");				
				if (isEntity) {
					
					EntityList el = EntityList.load(ac, "type(\"" + scope + "\")");

					// BEGIN entities
					if ((el != null) && (el.hasEntities())) {

						el.getEntities().forEach((id, ent) -> {
							String[] sscope = new String[1];
							sscope[0] = id;

							// get settings for entity
							SettingsObjectList sol = SettingsObjectList.load(ac, sschemas, sscope);

							// if settings exist for current entity
							if (sol.hasItems()) {
								try {
									// create folder
									File fe = new File(workdir + "\\" + scope + "\\" + id);
									fe.mkdirs();
									// get detailed entity
									// dump entity
									JSONObject entity = MonitoredEntities.getEntity(ac, id);

									FileWriter ew = new FileWriter(
											workdir + "\\" + scope + "\\" + id + "\\" + id + ".json");
									entity.write(ew, 2, 0);
									ew.close();
									// dump settings

									sol.getItems().forEach((cfgid, config) -> {
										
										try {
											writeSchema(workdir + "\\" + scope + "\\" + id,SettingsObjects.getSettingsObject(ac, cfgid),schemadetails.get(((SettingsObjectSummary)config).getSchemaid()).getRaw().getBoolean("multiObject"));
										} catch (Exception e) {
											System.err.println("ERROR while writing entity settings for: " + id + " :: " + cfgid);
										}
										
/*										
										String foldername;
										String filename;
										
										SettingsSchemaDetail ssd=schemadetails.get(((SettingsObjectSummary)config).getSchemaid());
										
										if (ssd.getRaw().getBoolean("multiObject")) {
											foldername=workdir + "\\" + scope + "\\" + id + "\\" + ((SettingsObjectSummary)config).getSchemaid().replaceAll(":", "_");
										    filename=workdir + "\\" + scope + "\\" + id + "\\" + ((SettingsObjectSummary)config).getSchemaid().replaceAll(":", "_")  +"\\"+ cfgid + ".json"	;										
										} else {
											foldername=workdir + "\\" + scope + "\\" + id + "\\" + ((SettingsObjectSummary)config).getSchemaid().replaceAll(":", "_");
										    filename=workdir + "\\" + scope + "\\" + id + "\\" + ((SettingsObjectSummary)config).getSchemaid().replaceAll(":", "_")  +"\\"+ cfgid + ".json"	;										
										}
										
										
										// create folder
										File fe2 = new File(workdir + "\\" + scope + "\\" + id + "\\" + ((SettingsObjectSummary)config).getSchemaid().replaceAll(":", "_"));
										fe2.mkdirs();

										try {
											FileWriter cw = new FileWriter(
													workdir + "\\" + scope + "\\" + id + "\\" + ((SettingsObjectSummary)config).getSchemaid().replaceAll(":", "_")  +"\\"+ cfgid + ".json");
											SettingsObjects.getSettingsObject(ac, cfgid).write(cw, 2, 0);
											cw.close();
										} catch (Exception e2) {
											System.err.println(
													"ERROR while writing entity settings for: " + id + " :: " + cfgid);
										}
*/										
									});

								} catch (Exception e) {
									System.err.println("ERROR while writing entity settings for: " + id);
								}

							}

						});
						
					// END entities
					}					
				} else {
					
					String[] sscope = new String[1];
					sscope[0] = scope;
					
					System.out.println("Getting objects for non-entity: "+scope);

					// get settings for entity
					SettingsObjectList sol = SettingsObjectList.load(ac, sschemas, sscope);

					// if settings exist for nonEntity
					if (sol.hasItems()) {
						try {
							// dump settings

							sol.getItems().forEach((cfgid, config) -> {
								try {
									writeSchema(workdir + "\\" + scope,SettingsObjects.getSettingsObject(ac, cfgid),schemadetails.get(((SettingsObjectSummary)config).getSchemaid()).getRaw().getBoolean("multiObject"));
								} catch (Exception e) {
									System.err.println("ERROR while writing settings for: " + scope +" :: "+ cfgid);
									e.printStackTrace(System.err);
								}

/*								
								// create folder
								File fe = new File(workdir + "\\" + scope + "\\" + ((SettingsObjectSummary)config).getSchemaid().replaceAll(":", "_"));
								fe.mkdirs();

								try {
									FileWriter cw = new FileWriter(workdir + "\\" + scope + "\\" + ((SettingsObjectSummary)config).getSchemaid().replaceAll(":", "_") +"\\"+cfgid + ".json");
									SettingsObjects.getSettingsObject(ac, cfgid).write(cw, 2, 0);
									cw.close();
								} catch (Exception e2) {
									System.err.println(
											"ERROR while writing entity settings for: " + scope + " :: " + cfgid);
								}
*/								
							});

						} catch (Exception e) {
							System.err.println("ERROR while writing entity settings for: " + scope);
						}

					}

				}

			});

//			System.out.println(ssl);
		} catch (Exception e) {
			e.printStackTrace(System.err);
		}

	}
	
	private static void writeSchema(String base,JSONObject schema, boolean multi) {
		
		String foldername;
		String filename;
		
		if (multi) {
			foldername=base+"\\"+normalize(schema.getString("schemaId"));
			
			if (schema.getJSONObject("value").has("name"))
				filename=  base+"\\"+normalize(schema.getString("schemaId"))+"\\"+normalize(schema.getJSONObject("value").getString("name"))+".json";
			else
				filename=  base+"\\"+normalize(schema.getString("schemaId"))+"\\"+normalize(schema.getString("objectId"))+".json";				
		} else {
			foldername=base;
			filename=  base+"\\"+normalize(schema.getString("schemaId"))+".json";
			
		}
		

		// create folder
		File fe = new File(foldername);
		fe.mkdirs();

		try {
			FileWriter cw = new FileWriter(filename);
			schema.write(cw, 2, 0);
			cw.close();
		} catch (Exception e) {
			System.err.println("ERROR while writing settings.");
			e.printStackTrace(System.err);
		}
	}
	
	private static String normalize(String escapeme) {
		return escapeme.replaceAll(":","_").
				replaceAll("<","_").
				replaceAll(">","_");
		
	}
	

}