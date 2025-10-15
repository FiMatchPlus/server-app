package com.fimatchplus.backend.portfolio.repository;

import com.fimatchplus.backend.portfolio.domain.Rules;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RulesRepository extends MongoRepository<Rules, String> {
}
