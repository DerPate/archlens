package com.example.xml;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ProgrammaticService {
    private final TransactionTemplate transactions;

    public ProgrammaticService(TransactionTemplate transactions) {
        this.transactions = transactions;
    }

    public void execute() {
        transactions.executeWithoutResult(status -> {});
    }
}
