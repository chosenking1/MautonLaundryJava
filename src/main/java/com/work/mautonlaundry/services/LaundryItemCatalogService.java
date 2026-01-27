package com.work.mautonlaundry.services;

import com.work.mautonlaundry.data.model.LaundryItemCatalog;
import com.work.mautonlaundry.data.repository.LaundryItemCatalogRepository;
import com.work.mautonlaundry.dtos.requests.laundryitemrequests.LaundryItemRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LaundryItemCatalogService {
    
    private final LaundryItemCatalogRepository catalogRepository;

    public List<LaundryItemCatalog> getAllActiveItems() {
        return catalogRepository.findByIsActiveTrue();
    }

    public LaundryItemCatalog getItemById(Long id) {
        return catalogRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Laundry item not found"));
    }

    public LaundryItemCatalog createItem(LaundryItemRequest request) {
        LaundryItemCatalog item = new LaundryItemCatalog();
        item.setName(request.getName());
        item.setUnit(request.getUnit());
        item.setBasePriceColored(request.getBasePriceColored());
        item.setBasePriceWhite(request.getBasePriceWhite());
        
        return catalogRepository.save(item);
    }

    public LaundryItemCatalog updateItem(Long id, LaundryItemRequest request) {
        LaundryItemCatalog item = getItemById(id);
        
        item.setName(request.getName());
        item.setUnit(request.getUnit());
        item.setBasePriceColored(request.getBasePriceColored());
        item.setBasePriceWhite(request.getBasePriceWhite());
        item.setUpdatedAt(LocalDateTime.now());
        
        return catalogRepository.save(item);
    }

    public void deactivateItem(Long id) {
        LaundryItemCatalog item = getItemById(id);
        item.setIsActive(false);
        item.setUpdatedAt(LocalDateTime.now());
        
        catalogRepository.save(item);
    }
}