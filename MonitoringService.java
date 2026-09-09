package org.machinecoding;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.*;

public class MonitoringService {
    private final AlertingService alertingService;
    private final Queue<MonitoringEvent> queue;

    public MonitoringService(Queue<MonitoringEvent> queue) {
        this.queue = queue;
        this.alertingService = new AlertingService();
    }

    public void setAlert(String productId, int threshold, AlertListener listener, AlertType type) {
        alertingService.setAlert(productId, threshold, listener, type);
    }

    public void checkAndFireAlerts() {
        while (!queue.isEmpty()) {
            MonitoringEvent event = queue.poll();
            if (event == null) {
                continue;
            }
            alertingService.processEvent(event);
        }
    }
}

class AlertingService {
    private final Map<String, List<AlertConfig>> configs;
    private final Map<AlertType, AlertRuleStrategy> strategies;

    public AlertingService() {
        this.configs = new HashMap<>();
        this.strategies = new HashMap<>();

        registerStrategies();
    }

    private void registerStrategies() {
        strategies.put(AlertType.LOW_STOCK, new LowStockStrategy());
        strategies.put(AlertType.SALE_SPIKE, new SalesSpikeStrategy());
        strategies.put(AlertType.EXPIRED_ITEM, new ExpiredItemStrategy());
    }

    public void setAlert(String productId, int threshold, AlertListener listener, AlertType type) {
        if (productId == null || listener == null || type == null) {
            throw new IllegalArgumentException("Invalid alert configuration");}

        if (threshold < 0) {
            throw new IllegalArgumentException("Threshold cannot be negative");}

        AlertConfig config = new AlertConfig(listener, threshold, type);
        configs.computeIfAbsent(productId, key -> new ArrayList<>()).add(config);
    }

    public void processEvent(MonitoringEvent event) {
        List<AlertConfig> productConfigs = configs.get(event.getProductId());
        if (productConfigs == null || productConfigs.isEmpty()) {
            return;
        }

        for (AlertConfig config : productConfigs) {
            AlertRuleStrategy strategy = strategies.get(config.getType());
            if (strategy.isTriggered(event, config.getThreshold())) {
                Alert alert = strategy.createAlert(event, config);
                fireAlert(alert);
            }
        }
    }

    private void fireAlert(Alert alert) {
        alert.getListener().onAlert(alert);
    }
}

interface AlertRuleStrategy {
    boolean isTriggered(MonitoringEvent event, int threshold);
    Alert createAlert(MonitoringEvent event, AlertConfig config);
}

class LowStockStrategy implements AlertRuleStrategy {
    @Override
    public boolean isTriggered(MonitoringEvent event, int threshold) {
        if (!(event instanceof SaleEvent)) {
            return false;
        }
        SaleEvent saleEvent = (SaleEvent) event;
        return saleEvent.getRemainingQuantity() <= threshold;
    }

    @Override
    public Alert createAlert(MonitoringEvent event, AlertConfig config) {
        SaleEvent saleEvent = (SaleEvent) event;
        String message = "Low stock detected for product " + event.getProductId() +
                ". Remaining quantity: " + saleEvent.getRemainingQuantity();

        return new Alert(UUID.randomUUID().toString(), event.getProductId(),
                AlertType.LOW_STOCK, message, event.getTimestamp(), config.getListener());
    }
}

class SalesSpikeStrategy implements AlertRuleStrategy {
    @Override
    public boolean isTriggered(MonitoringEvent event, int threshold) {
        if (!(event instanceof SaleEvent)) {
            return false;
        }
        SaleEvent saleEvent = (SaleEvent) event;
        return saleEvent.getQuantitySold() >= threshold;
    }

    @Override
    public Alert createAlert(MonitoringEvent event, AlertConfig config) {
        SaleEvent saleEvent = (SaleEvent) event;
        String message = "Sales spike detected for product " + event.getProductId() +
                ". Quantity sold: " + saleEvent.getQuantitySold();

        return new Alert(UUID.randomUUID().toString(), event.getProductId(),
                AlertType.SALE_SPIKE, message, event.getTimestamp(), config.getListener());
    }
}

class ExpiredItemStrategy implements AlertRuleStrategy {
    @Override
    public boolean isTriggered(MonitoringEvent event, int threshold) {

        if (!(event instanceof DailyCheckEvent)) {
            return false;
        }

        DailyCheckEvent dailyCheckEvent = (DailyCheckEvent) event;
        return dailyCheckEvent.getDaysToExpiry() <= threshold;
    }

    @Override
    public Alert createAlert(MonitoringEvent event, AlertConfig config) {
        DailyCheckEvent expiryEvent = (DailyCheckEvent) event;
        String message;

        if (expiryEvent.getDaysToExpiry() < 0) {
            message = "Product " + event.getProductId() + " has already expired.";
        } else {
            message = "Product " + event.getProductId() + " is expiring soon.";
        }

        return new Alert(UUID.randomUUID().toString(), event.getProductId(),
                AlertType.EXPIRED_ITEM, message, event.getTimestamp(), config.getListener());
    }
}

@Getter
@AllArgsConstructor
class AlertConfig {
    private final AlertListener listener;
    private final int threshold;
    private final AlertType type;
}

@Getter
@AllArgsConstructor
class Alert {
    private final String id;
    private final String productId;
    private final AlertType type;
    private final String message;
    private final Instant timestamp;
    private final AlertListener listener;
}

interface AlertListener {
    void onAlert(Alert alert);
}

@AllArgsConstructor
class EmailAlertListener implements AlertListener {
    private final String email;

    @Override
    public void onAlert(Alert alert) {

    }
}

@AllArgsConstructor
class WebhookAlertListener implements AlertListener {
    private final String webhookUrl;

    @Override
    public void onAlert(Alert alert) {

    }
}

@Getter
class MonitoringEvent {
    private final String productId;
    private final Instant timestamp;

    public MonitoringEvent(String productId, Instant timestamp) {
        this.productId = productId;
        this.timestamp = timestamp;
    }
}

@Getter
class SaleEvent extends MonitoringEvent {

    private final int quantitySold;
    private final int remainingQuantity;

    public SaleEvent(String productId, int quantitySold, int remainingQuantity, Instant timestamp) {
        super(productId, timestamp);
        if (quantitySold < 0) {
            throw new IllegalArgumentException("Quantity sold cannot be negative");
        }

        if (remainingQuantity < 0) {
            throw new IllegalArgumentException("Remaining quantity cannot be negative");
        }

        this.quantitySold = quantitySold;
        this.remainingQuantity = remainingQuantity;
    }
}

@Getter
class DailyCheckEvent extends MonitoringEvent {
    private final int daysToExpiry;
    public DailyCheckEvent(String productId, int daysToExpiry, Instant timestamp) {
        super(productId, timestamp);
        this.daysToExpiry = daysToExpiry;
    }
}

enum AlertType {
    LOW_STOCK,
    SALE_SPIKE,
    EXPIRED_ITEM
}
