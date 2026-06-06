package com.vanlo.newsfetch.application;

import com.vanlo.newsfetch.api.FetchNewsRequest;
import com.vanlo.newsfetch.api.FetchNewsResponse;
import com.vanlo.newsfetch.domain.FetchStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FetchNewsUseCaseTest {

    @Test
    void mapsRequestToOrchestratorCommandAndAppliesDefaultLimit() {
        FetchOrchestrator orchestrator = mock(FetchOrchestrator.class);
        when(orchestrator.fetch(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new FetchOrchestrationResult(FetchStatus.OK, List.of(), List.of()));
        FetchNewsUseCase useCase = new FetchNewsUseCase(orchestrator);

        FetchNewsResponse response = useCase.fetch(new FetchNewsRequest(List.of("rss-a"), "world", "en", "GB", null));

        ArgumentCaptor<FetchNewsCommand> commandCaptor = ArgumentCaptor.forClass(FetchNewsCommand.class);
        verify(orchestrator).fetch(commandCaptor.capture());
        FetchNewsCommand command = commandCaptor.getValue();
        assertThat(command.sourceIds()).containsExactly("rss-a");
        assertThat(command.category()).isEqualTo("world");
        assertThat(command.language()).isEqualTo("en");
        assertThat(command.region()).isEqualTo("GB");
        assertThat(command.limit()).isEqualTo(20);
        assertThat(response.status()).isEqualTo(FetchStatus.OK);
    }

    @Test
    void treatsNullRequestAsEmptyRequest() {
        FetchOrchestrator orchestrator = mock(FetchOrchestrator.class);
        when(orchestrator.fetch(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new FetchOrchestrationResult(FetchStatus.OK, List.of(), List.of()));
        FetchNewsUseCase useCase = new FetchNewsUseCase(orchestrator);

        useCase.fetch(null);

        ArgumentCaptor<FetchNewsCommand> commandCaptor = ArgumentCaptor.forClass(FetchNewsCommand.class);
        verify(orchestrator).fetch(commandCaptor.capture());
        FetchNewsCommand command = commandCaptor.getValue();
        assertThat(command.sourceIds()).isEmpty();
        assertThat(command.category()).isNull();
        assertThat(command.language()).isNull();
        assertThat(command.region()).isNull();
        assertThat(command.limit()).isEqualTo(20);
    }
}
