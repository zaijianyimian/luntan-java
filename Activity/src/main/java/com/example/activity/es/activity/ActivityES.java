package com.example.activity.es.activity;

import com.example.activity.pojo.ActivityESSave;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityES extends ElasticsearchRepository<ActivityESSave, Integer> {
        Page<ActivityESSave> findByCategoryId(Integer categoryId, Pageable pageable);
        Page<ActivityESSave> findAllBy(Pageable pageable);
}
