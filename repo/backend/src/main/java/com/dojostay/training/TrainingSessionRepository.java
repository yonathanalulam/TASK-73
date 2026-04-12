package com.dojostay.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface TrainingSessionRepository
        extends JpaRepository<TrainingSession, Long>, JpaSpecificationExecutor<TrainingSession> {

    List<TrainingSession> findByTrainingClassId(Long trainingClassId);
}
