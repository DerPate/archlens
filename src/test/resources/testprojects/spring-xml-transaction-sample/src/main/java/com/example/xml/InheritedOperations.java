package com.example.xml;

import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface InheritedOperations {
    String inspect();
}
