package com.dojostay.users;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserLockStateRepository extends JpaRepository<UserLockState, Long> {
}
