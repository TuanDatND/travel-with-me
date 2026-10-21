package com.coc.sba_treektour.modules.event.repository;

import com.coc.sba_treektour.modules.event.entity.EventImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<EventImage, Long> {}
