package com.jenkins.plugins.rally.connector;

import com.jenkins.plugins.rally.RallyException;
import com.jenkins.plugins.rally.config.AdvancedConfiguration;
import com.jenkins.plugins.rally.config.RallyConfiguration;
import com.jenkins.plugins.rally.scm.ScmConnector;
import com.rallydev.rest.request.QueryRequest;
import com.rallydev.rest.response.QueryResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RallyConnectorTest {
    @Mock
    private RallyApi rallyApi;

    @Mock
    private ScmConnector scmConnector;

    private RallyConnector connector;

    @Before
    public void setUp() throws Exception {
        this.connector = new RallyConnector();
        this.connector.setRallyConfiguration(new RallyConfiguration("API_KEY", "WORKSPACE_NAME", "SCM_NAME"));
        this.connector.setRallyApiInstance(rallyApi);
        this.connector.setScmConnector(scmConnector);
        this.connector.setAdvancedConfiguration(new AdvancedConfiguration("http://proxy.url", "false"));
    }

    @Test(expected= RallyException.class)
    public void shouldThrowExceptionIfAttemptToUpdateNonStory() throws Exception {
        RallyDetailsDTO details = new RallyDetailsDTO();
        details.setStory(false);

        this.connector.updateRallyTaskDetails(details);
    }

    @Test(expected=RallyException.class)
    public void shouldThrowExceptionIfAttemptToUpdateWithoutTaskIndexAndTaskId() throws Exception {
        RallyDetailsDTO details = new RallyDetailsDTO();
        details.setStory(true);
        details.setTaskIndex("");
        details.setTaskID("");

        this.connector.updateRallyTaskDetails(details);
    }

    @Test(expected=RallyException.class)
    public void shouldThrowErrorIfAttemptToUpdateWithoutValidStoryRef() throws Exception {
        QueryResponse emptyResponse = new QueryResponse(getEmptyQuery());
        when(this.rallyApi.query(any(QueryRequest.class))).thenReturn(emptyResponse);

        RallyDetailsDTO details = new RallyDetailsDTO();
        details.setStory(true);
        details.setTaskIndex("1");
        details.setTaskID("12345");

        this.connector.updateRallyTaskDetails(details);
    }

    private String getEmptyQuery() {
        return "{\"QueryResult\": " +
                "{\"_rallyAPIMajor\": \"2\", \"_rallyAPIMinor\": \"0\", \"Errors\": [], \"Warnings\": []," +
                " \"TotalResultCount\": 0, \"StartIndex\": 1, \"PageSize\": 20, \"Results\": []}}";
    }
}