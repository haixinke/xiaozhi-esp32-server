# ACK 部署：Kubernetes 资源清单

本文件提供部署到 ACK 所需的完整 K8s YAML 清单，包括 Namespace、ConfigMap、Secret、Deployment、Service、Ingress、HPA 等。

## 1. Namespace

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: xiaozhi
```

## 2. 共享配置 ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: xiaozhi-shared-config
  namespace: xiaozhi
data:
  MANAGER_API_URL: "http://manager-api.xiaozhi.svc.cluster.local:8002/xiaozhi"
  REDIS_HOST: "your-redis.rds.aliyuncs.com"
  REDIS_PORT: "6379"
  REDIS_DB: "0"
  MYSQL_HOST: "your-mysql.rds.aliyuncs.com"
  MYSQL_PORT: "3306"
  MYSQL_DATABASE: "egg_database"
```

## 3. manager-api Secret

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: manager-api-secret
  namespace: xiaozhi
type: Opaque
stringData:
  MYSQL_USERNAME: "xiaozhi"
  MYSQL_PASSWORD: "<your-strong-password>"
  REDIS_PASSWORD: "<your-redis-password>"
  WECHAT_MINIPROGRAM_SECRET: "<wechat-mini-secret>"
```

> 生产环境建议使用 **阿里云 KMS + ACK Secret 托管** 或 **OOS 加密参数** 注入。

## 4. manager-api Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: manager-api
  namespace: xiaozhi
  labels:
    app: manager-api
spec:
  replicas: 2
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: manager-api
  template:
    metadata:
      labels:
        app: manager-api
    spec:
      containers:
        - name: manager-api
          image: registry-vpc.cn-hangzhou.aliyuncs.com/your-namespace/manager-api:VERSION
          imagePullPolicy: Always
          ports:
            - containerPort: 8002
              name: http
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
            - name: SERVER_PORT
              value: "8002"
            - name: SPRING_DATASOURCE_DRUID_URL
              value: "jdbc:mysql://$(MYSQL_HOST):$(MYSQL_PORT)/$(MYSQL_DATABASE)?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
            - name: SPRING_DATASOURCE_DRUID_USERNAME
              valueFrom:
                secretKeyRef:
                  name: manager-api-secret
                  key: MYSQL_USERNAME
            - name: SPRING_DATASOURCE_DRUID_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: manager-api-secret
                  key: MYSQL_PASSWORD
            - name: SPRING_DATA_REDIS_HOST
              valueFrom:
                configMapKeyRef:
                  name: xiaozhi-shared-config
                  key: REDIS_HOST
            - name: SPRING_DATA_REDIS_PORT
              valueFrom:
                configMapKeyRef:
                  name: xiaozhi-shared-config
                  key: REDIS_PORT
            - name: SPRING_DATA_REDIS_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: manager-api-secret
                  key: REDIS_PASSWORD
            - name: WECHAT_MINIPROGRAM_SECRET
              valueFrom:
                secretKeyRef:
                  name: manager-api-secret
                  key: WECHAT_MINIPROGRAM_SECRET
          resources:
            requests:
              cpu: "500m"
              memory: "1Gi"
            limits:
              cpu: "2"
              memory: "4Gi"
          livenessProbe:
            httpGet:
              path: /xiaozhi/actuator/health
              port: 8002
            initialDelaySeconds: 60
            periodSeconds: 30
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /xiaozhi/actuator/health
              port: 8002
            initialDelaySeconds: 10
            periodSeconds: 10
            failureThreshold: 3
          startupProbe:
            httpGet:
              path: /xiaozhi/actuator/health
              port: 8002
            initialDelaySeconds: 10
            periodSeconds: 10
            failureThreshold: 30
```

## 5. manager-api Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: manager-api
  namespace: xiaozhi
spec:
  type: ClusterIP
  selector:
    app: manager-api
  ports:
    - port: 8002
      targetPort: 8002
      name: http
```

## 6. xiaozhi-server ConfigMap（基础配置）

ConfigMap 中只放非敏感的基础配置，应用在启动时会优先读取 `data/.config.yaml` 中的覆盖配置。

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: xiaozhi-server-config
  namespace: xiaozhi
data:
  config.yaml: |
    server:
      ip: 0.0.0.0
      port: 8000
      http_port: 8003
      websocket: ws://your-domain:8000/xiaozhi/v1/
      vision_explain: http://your-domain:8003/mcp/vision/explain
      auth:
        enabled: true
    log:
      log_level: INFO
      log_dir: /app/tmp
      data_dir: /app/data
    delete_audio: true
    selected_module:
      VAD: SileroVAD
      ASR: FunASR
      LLM: DoubaoLLM
      TTS: EdgeTTS
      Memory: nomem
      Intent: function_call
    manager-api:
      url: http://manager-api.xiaozhi.svc.cluster.local:8002/xiaozhi
```

## 7. xiaozhi-server Secret（覆盖配置）

Secret 以 `data/.config.yaml` 形式挂载，覆盖基础配置中的敏感项和环境相关项。

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: xiaozhi-server-config-secret
  namespace: xiaozhi
type: Opaque
stringData:
  .config.yaml: |
    manager-api:
      url: http://manager-api.xiaozhi.svc.cluster.local:8002/xiaozhi
      secret: "<从智控台 server.secret 获取>"
    LLM:
      DoubaoLLM:
        api_key: "<your-llm-key>"
    TTS:
      EdgeTTS:
        # 若 TTS 提供商需要密钥，在此配置
        api_key: "<your-tts-key>"
    ASR:
      FunASR:
        # 若使用云端 ASR，在此配置
        api_key: "<your-asr-key>"
```

## 8. xiaozhi-server Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: xiaozhi-server
  namespace: xiaozhi
  labels:
    app: xiaozhi-server
spec:
  replicas: 2
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: xiaozhi-server
  template:
    metadata:
      labels:
        app: xiaozhi-server
    spec:
      initContainers:
        - name: model-loader
          image: registry.cn-hangzhou.aliyuncs.com/your-namespace/model-loader:latest
          command: ["/bin/sh", "-c", "cp /shared/models/SenseVoiceSmall/model.pt /app/models/SenseVoiceSmall/"]
          volumeMounts:
            - name: models
              mountPath: /app/models
          securityContext:
            runAsUser: 1001
      containers:
        - name: xiaozhi-server
          image: registry-vpc.cn-hangzhou.aliyuncs.com/your-namespace/xiaozhi-server:VERSION
          imagePullPolicy: Always
          ports:
            - containerPort: 8000
              name: websocket
            - containerPort: 8003
              name: http
          env:
            - name: TZ
              value: "Asia/Shanghai"
          volumeMounts:
            - name: config
              mountPath: /app/config.yaml
              subPath: config.yaml
            - name: config-secret
              mountPath: /app/data/.config.yaml
              subPath: .config.yaml
            - name: data
              mountPath: /app/data
            - name: models
              mountPath: /app/models
            - name: tmp
              mountPath: /app/tmp
          resources:
            requests:
              cpu: "1"
              memory: "2Gi"
            limits:
              cpu: "4"
              memory: "8Gi"
          livenessProbe:
            httpGet:
              path: /mcp/vision/explain
              port: 8003
            initialDelaySeconds: 60
            periodSeconds: 30
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /mcp/vision/explain
              port: 8003
            initialDelaySeconds: 10
            periodSeconds: 10
            failureThreshold: 3
          startupProbe:
            httpGet:
              path: /mcp/vision/explain
              port: 8003
            initialDelaySeconds: 10
            periodSeconds: 10
            failureThreshold: 60
      volumes:
        - name: config
          configMap:
            name: xiaozhi-server-config
        - name: config-secret
          secret:
            secretName: xiaozhi-server-config-secret
        - name: data
          persistentVolumeClaim:
            claimName: xiaozhi-data-pvc
        - name: models
          persistentVolumeClaim:
            claimName: xiaozhi-models-pvc
        - name: tmp
          emptyDir: {}
```

## 9. xiaozhi-server Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: xiaozhi-server
  namespace: xiaozhi
  annotations:
    service.beta.kubernetes.io/alibaba-cloud-loadbalancer-type: "nlb"
spec:
  type: LoadBalancer
  selector:
    app: xiaozhi-server
  ports:
    - port: 8000
      targetPort: 8000
      name: websocket
    - port: 8003
      targetPort: 8003
      name: http
```

> WebSocket 对长连接敏感，建议为 `8000` 使用 **NLB** 或 **ALB 的 TCP/WS 监听**。

## 10. Ingress（manager-api 七层入口）

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: manager-api-ingress
  namespace: xiaozhi
  annotations:
    alb.ingress.kubernetes.io/scheme: internet
    alb.ingress.kubernetes.io/address-type: internet
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
    alb.ingress.io/certificate-ids: "<your-cert-id>"
spec:
  ingressClassName: alb
  rules:
    - host: api.your-domain.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: manager-api
                port:
                  number: 8002
```

## 11. HPA 示例

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: manager-api-hpa
  namespace: xiaozhi
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: manager-api
  minReplicas: 2
  maxReplicas: 6
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
```

xiaozhi-server 的 HPA 类似，建议 `minReplicas: 2`，`maxReplicas: 10`。
