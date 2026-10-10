package com.coc.sba_treektour.event.repository;

import com.coc.sba_treektour.event.entity.EventImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<EventImage, Long> {}
