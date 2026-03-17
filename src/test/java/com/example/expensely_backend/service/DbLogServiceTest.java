package com.example.expensely_backend.service;

import com.example.expensely_backend.model.ApiRequestLog;
import com.example.expensely_backend.model.FunctionLog;
import com.example.expensely_backend.repository.ApiRequestLogRepository;
import com.example.expensely_backend.repository.FunctionLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.Mockito.times;

public class DbLogServiceTest {

    @Test
    void flushesApiAndFunctionLogsInBatches() {
        ApiRequestLogRepository apiRepo = Mockito.mock(ApiRequestLogRepository.class);
        FunctionLogRepository functionRepo = Mockito.mock(FunctionLogRepository.class);
        DbLogService service = new DbLogService(apiRepo, functionRepo, 2);

        service.logApi(new ApiRequestLog());
        service.logApi(new ApiRequestLog());
        service.logFunction(new FunctionLog());
        service.logFunction(new FunctionLog());

        Mockito.verify(apiRepo, times(1)).saveAll(Mockito.any(List.class));
        Mockito.verify(functionRepo, times(1)).saveAll(Mockito.any(List.class));
    }
}

