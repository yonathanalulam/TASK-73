package com.dojostay.ops;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OpsJobRecordRepository extends JpaRepository<OpsJobRecord, Long> {

    List<OpsJobRecord> findTop20ByJobKindOrderByStartedAtDesc(OpsJobRecord.JobKind jobKind);

    List<OpsJobRecord> findTop50ByOrderByStartedAtDesc();
}
