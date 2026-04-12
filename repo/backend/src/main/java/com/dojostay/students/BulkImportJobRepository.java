package com.dojostay.students;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BulkImportJobRepository extends JpaRepository<BulkImportJob, Long> {
}
