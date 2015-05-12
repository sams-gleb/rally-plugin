package com.jenkins.plugins.rally.connector;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.lang.StringUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rallydev.rest.RallyRestApi;
import com.rallydev.rest.request.CreateRequest;
import com.rallydev.rest.request.QueryRequest;
import com.rallydev.rest.request.UpdateRequest;
import com.rallydev.rest.response.CreateResponse;
import com.rallydev.rest.response.QueryResponse;
import com.rallydev.rest.response.Response;
import com.rallydev.rest.response.UpdateResponse;
import com.rallydev.rest.util.Fetch;
import com.rallydev.rest.util.QueryFilter;

public class RallyConnector {
	private final String userName;
	private final String apiKey;
	private final String workspace;
	private final String project;
	private final String scmuri;
	private final String scmRepoName;	
	
	private final RallyRestApi restApi;
	
	public static final String RALLY_URL = "https://rally1.rallydev.com";
	public static final String APPLICATION_NAME = "RallyConnect";
	public static final String WSAPI_VERSION = "v2.0";
	private String DEFAULT_REPO_NAME_CREATED_BY_PLUGIN = "plugin_repo";
	
	public RallyConnector(final String userName, final String apiKey, final String workspace, final String project, final String scmuri, final String scmRepoName, final String proxy) throws URISyntaxException {
		this.userName = userName;
        this.apiKey = apiKey;
    	this.workspace = workspace;
    	this.project = project;
    	this.scmuri = scmuri;
    	this.scmRepoName = scmRepoName;
    	
    	restApi = new RallyRestApi(new URI(RALLY_URL), apiKey);
    	restApi.setWsapiVersion(WSAPI_VERSION);
        restApi.setApplicationName(APPLICATION_NAME);
        if(proxy != null && proxy.trim().length() > 0){
            restApi.setProxy(new URI(proxy));
        }
	}
	
	public void closeConnection() throws IOException {
		restApi.close();
	}
	
	public boolean updateRallyChangeSet(RallyDetailsDTO rdto) throws IOException {
		rdto.getOut().println("Updating Rally -- " + rdto.getMsg());
		JsonObject newChangeset = createChangeSet(rdto);
	    CreateRequest createRequest = new CreateRequest("Changeset", newChangeset);
	    CreateResponse createResponse = restApi.create(createRequest);
    	printWarnningsOrErrors(createResponse, rdto, "updateRallyChangeSet.CreateChangeSet");
	    String csRef = createResponse.getObject().get("_ref").getAsString();	    
	    for(int i=0; i<rdto.getFileNameAndTypes().length;i++) {
	    	JsonObject newChange = createChange(csRef, rdto.getFileNameAndTypes()[i][0], rdto.getFileNameAndTypes()[i][1]);	    
	    	CreateRequest cRequest = new CreateRequest("change", newChange);
	    	CreateResponse cResponse = restApi.create(cRequest);
    		printWarnningsOrErrors(cResponse, rdto, "updateRallyChangeSet. CreateChange");
	    }
	    return createResponse.wasSuccessful();
    }
	
	private JsonObject createChangeSet(RallyDetailsDTO rdto) throws IOException {
		JsonObject newChangeset = new JsonObject();
		JsonObject scmJsonObject = createSCMRef(rdto);		
        newChangeset.add("SCMRepository", scmJsonObject); 
        //newChangeset.addProperty("Author", createUserRef());
       	newChangeset.addProperty("Revision", rdto.getRevison());
        newChangeset.addProperty("Uri", scmuri);
        newChangeset.addProperty("CommitTimestamp", rdto.getTimeStamp());
        newChangeset.addProperty("Message", rdto.getMsg());
        //newChangeset.addProperty("Builds", createBuilds());        
           
        JsonArray artifactsJsonArray = new JsonArray();
        JsonObject ref; 
        if(rdto.isStory())
			ref = createStoryRef(rdto);
		else	
			ref = createDefectRef(rdto);
        artifactsJsonArray.add(ref);
        newChangeset.add("Artifacts", artifactsJsonArray);
        return newChangeset;
	}
	
	private JsonObject createStoryRef(RallyDetailsDTO rdto) throws IOException {
        QueryRequest  storyRequest = new QueryRequest("HierarchicalRequirement");
        storyRequest.setFetch(new Fetch("FormattedID","Name","Changesets"));
        storyRequest.setQueryFilter(new QueryFilter("FormattedID", "=", rdto.getId()));
        storyRequest.setWorkspace(workspace);
        QueryResponse storyQueryResponse = restApi.query(storyRequest);
        printWarnningsOrErrors(storyQueryResponse, rdto, "createStoryRef");
        JsonObject storyJsonObject = storyQueryResponse.getResults().get(0).getAsJsonObject();
        return storyJsonObject;
	}
	
	private JsonObject createDefectRef(RallyDetailsDTO rdto) throws IOException {
		QueryRequest defectRequest = new QueryRequest("defect");
		defectRequest.setFetch(new Fetch("FormattedId", "Name", "Changesets"));
		defectRequest.setQueryFilter(new QueryFilter("FormattedID", "=", rdto.getId()));
		defectRequest.setWorkspace(workspace);		
		defectRequest.setScopedDown(true);
		QueryResponse defectResponse = restApi.query(defectRequest);
		printWarnningsOrErrors(defectResponse, rdto, "createDefectRef");
		JsonObject defectJsonObject = defectResponse.getResults().get(0).getAsJsonObject();
		return defectJsonObject;
	}
	
	private JsonObject createChange(String csRef, String fileName, String fileType) {
		JsonObject newChange = new JsonObject();
	    newChange.addProperty("PathAndFilename", fileName);
	    newChange.addProperty("Action", fileType);	    
	    newChange.addProperty("Uri", scmuri);
        newChange.addProperty("Changeset", csRef);
        return newChange;
	}
		
	public boolean updateRallyTaskDetails(RallyDetailsDTO rdto) throws IOException {
		boolean result = false;
		if(rdto.isStory() && (!rdto.getTaskIndex().isEmpty() || !rdto.getTaskID().isEmpty())) {
			JsonObject storyRef = createStoryRef(rdto);
			JsonObject taskRef;
			if(!rdto.getTaskIndex().isEmpty()) {
				int ti = Integer.parseInt(rdto.getTaskIndex());
				ti = ti - 1; //index starts with 0 in rally
				taskRef = createTaskRef(storyRef.get("_ref").toString(), "TaskIndex", String.valueOf(ti), rdto);
			} else {
				taskRef = createTaskRef(storyRef.get("_ref").toString(), "FormattedID", rdto.getTaskID(), rdto);
			}	
			 
			JsonObject updateTask = new JsonObject();
			if(!rdto.getTaskStatus().isEmpty())
				updateTask.addProperty("State", rdto.getTaskStatus());
			else {
				updateTask.addProperty("State", "In-Progress");
			}
			if(!rdto.getTaskToDO().isEmpty()) {
				Double todo = Double.parseDouble(rdto.getTaskToDO());
				updateTask.addProperty("ToDo", String.valueOf(todo));
			}
			if(!rdto.getTaskActuals().isEmpty()) {
				Double actuals = Double.parseDouble(rdto.getTaskActuals());
				try {
					actuals = actuals + taskRef.get("Actuals").getAsDouble();
				} catch(Exception e) {}	
				updateTask.addProperty("Actuals", String.valueOf(actuals));
			}
			if(!rdto.getTaskEstimates().isEmpty()) {
				Double estimates = Double.parseDouble(rdto.getTaskEstimates());
				updateTask.addProperty("Estimate", String.valueOf(estimates));
			}
	        
	        UpdateRequest updateRequest = new UpdateRequest(taskRef.get("_ref").getAsString(), updateTask);
	        UpdateResponse updateResponse = restApi.update(updateRequest);
	        printWarnningsOrErrors(updateResponse, rdto, "updateRallyTaskDetails");
	        result = updateResponse.wasSuccessful();
		}
		return result;
	}
	
	private JsonObject createTaskRef(String storyRef, String taskQueryAttr, String taskQueryValue, RallyDetailsDTO rdto) throws IOException {
		QueryRequest taskRequest = new QueryRequest("Task");
        taskRequest.setFetch(new Fetch("FormattedID", "Actuals", "State"));
        QueryFilter qf = new QueryFilter("WorkProduct", "=", storyRef);
       	qf = qf.and(new QueryFilter(taskQueryAttr, "=", taskQueryValue));
        taskRequest.setQueryFilter(qf);
        QueryResponse taskQueryResponse = restApi.query(taskRequest);
        printWarnningsOrErrors(taskQueryResponse, rdto, "createTaskRef");
        JsonObject taskRef = taskQueryResponse.getResults().get(0).getAsJsonObject();
        return taskRef;
    }	

	private JsonObject createSCMRef(RallyDetailsDTO rdto) throws IOException {
        QueryRequest scmRequest = new QueryRequest("SCMRepository");
        scmRequest.setFetch(new Fetch("ObjectID","Name","SCMType"));
        scmRequest.setWorkspace(workspace);
       	scmRequest.setQueryFilter(new QueryFilter("Name", "=", getSCMRepoName(rdto, scmRepoName)));        
        QueryResponse scmQueryResponse = restApi.query(scmRequest);
        printWarnningsOrErrors(scmQueryResponse, rdto, "createSCMRef");
        JsonObject scmJsonObject = scmQueryResponse.getResults().get(0).getAsJsonObject();        
        return scmJsonObject;
	}
	
	private String getSCMRepoName(RallyDetailsDTO rdto, String scmRepoName) throws IOException {
		if(StringUtils.isNotBlank(scmRepoName)  && isProvidedScmRepoNameExist(rdto, scmRepoName))
			return scmRepoName;
		
		String anyOtherRepoName = getAnyOtherRepoName(rdto);
        if(!StringUtils.isBlank(anyOtherRepoName)) 
        	return anyOtherRepoName;       	
        
        if(isDefaultPluginRepoNameExist(rdto))
        	return DEFAULT_REPO_NAME_CREATED_BY_PLUGIN;
        
        return createDefaultPluginSCMReposirotyName(rdto);
	}

	private Boolean isProvidedScmRepoNameExist(RallyDetailsDTO rdto, String scmRepoName) throws IOException {
		QueryRequest scmRequest = new QueryRequest("SCMRepository");
        scmRequest.setFetch(new Fetch("ObjectID","Name","Name"));
        scmRequest.setQueryFilter(new QueryFilter("Name", "=", scmRepoName));
        scmRequest.setWorkspace(workspace);
        String providedRepoName = "";
        try {
        	QueryResponse scmQueryResponse = restApi.query(scmRequest);
        	printWarnningsOrErrors(scmQueryResponse, rdto, "isProvidedScmRepoNameExist");
        	JsonObject scmJsonObject = scmQueryResponse.getResults().get(0).getAsJsonObject();        
    		providedRepoName = scmJsonObject.get("_refObjectName").getAsString();
		} catch (Exception e) {
		}
		return StringUtils.isNotBlank(providedRepoName);
	}
	
	private String getAnyOtherRepoName(RallyDetailsDTO rdto) throws IOException {
		QueryRequest scmRequest = new QueryRequest("SCMRepository");
        scmRequest.setFetch(new Fetch("ObjectID", "Name", "Name"));
        scmRequest.setWorkspace(workspace);
        String anyOtherRepoName = "";
        try {
        	QueryResponse scmQueryResponse = restApi.query(scmRequest);
        	printWarnningsOrErrors(scmQueryResponse, rdto, "getAnyOtherRepoName");
        	JsonObject scmJsonObject = scmQueryResponse.getResults().get(0).getAsJsonObject();        
    		anyOtherRepoName = scmJsonObject.get("_refObjectName").getAsString();
		} catch (Exception e) {
		}
		return anyOtherRepoName;
	}
	
	private boolean isDefaultPluginRepoNameExist(RallyDetailsDTO rdto) throws IOException {
		QueryRequest scmRequest = new QueryRequest("SCMRepository");
        scmRequest.setFetch(new Fetch("ObjectID","Name","Name"));
        scmRequest.setQueryFilter(new QueryFilter("Name", "=", DEFAULT_REPO_NAME_CREATED_BY_PLUGIN));
        scmRequest.setWorkspace(workspace);
        String defaultPluginRepoName = "";
        try {
            QueryResponse scmQueryResponse = restApi.query(scmRequest);
            printWarnningsOrErrors(scmQueryResponse, rdto, "isDefaultPluginRepoNameExist");
            JsonObject scmJsonObject = scmQueryResponse.getResults().get(0).getAsJsonObject();        
            defaultPluginRepoName = scmJsonObject.get("_refObjectName").getAsString();
		} catch (Exception e) {
		}
		return StringUtils.isNotBlank(defaultPluginRepoName);
	}
	
	private String createDefaultPluginSCMReposirotyName(RallyDetailsDTO rdto) throws IOException {
		JsonObject newSCMRepository = new JsonObject();
        newSCMRepository.addProperty("Description", "This repository name is created by rally update plugin");        
        
		newSCMRepository.addProperty("Name", DEFAULT_REPO_NAME_CREATED_BY_PLUGIN);
        newSCMRepository.addProperty("SCMType", "GIT");
        if(!StringUtils.isBlank(scmuri))
        	newSCMRepository.addProperty("Uri", scmuri);
        CreateRequest createRequest = new CreateRequest("SCMRepository", newSCMRepository);
        System.out.println(createRequest.getBody());
        CreateResponse createResponse = restApi.create(createRequest);
        printWarnningsOrErrors(createResponse, rdto, "createDefaultPluginSCMReposirotyName");
        return DEFAULT_REPO_NAME_CREATED_BY_PLUGIN;
	}

	private JsonObject createUserRef(RallyDetailsDTO rdto) throws IOException {
		QueryRequest userRequest = new QueryRequest("User");
        userRequest.setFetch(new Fetch("UserName", "Subscription", "DisplayName"));
        userRequest.setQueryFilter(new QueryFilter("UserName", "=", userName));
        QueryResponse userQueryResponse = restApi.query(userRequest);
        printWarnningsOrErrors(userQueryResponse, rdto, "createUserRef");
        JsonArray userQueryResults = userQueryResponse.getResults();
        JsonElement userQueryElement = userQueryResults.get(0);
        JsonObject userQueryObject = userQueryElement.getAsJsonObject();        
        String userRef = userQueryObject.get("_ref").toString();
        return userQueryObject;
	}

	private void printWarnningsOrErrors(Response response, RallyDetailsDTO rdto, String methodName) {
		if (response.wasSuccessful() && rdto.isDebugOn()) {
			rdto.getOut().println("\tSucess From method: " + methodName);			
			rdto.printAllFields();
            String[] warningList;
            warningList = response.getWarnings();
            for (int i=0;i<warningList.length;i++) {
                rdto.getOut().println("\twarnning " + warningList[i]);
            }
        } else {
            String[] errorList;
            errorList = response.getErrors();
            if(errorList.length > 0) {
            	rdto.getOut().println("\tError From method: " + methodName);	
            	rdto.printAllFields();
            }	
            for (int i=0;i<errorList.length;i++) {
                rdto.getOut().println("\terror " + errorList[i]);
            }
        }
	}
}
