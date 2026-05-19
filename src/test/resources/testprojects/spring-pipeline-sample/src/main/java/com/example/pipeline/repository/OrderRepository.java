package com.example.pipeline.repository;

import com.example.pipeline.model.OrderEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, String> {
    List<OrderEntity> findByStatus(String status);
}
