<mxGraphModel dx="1277" dy="872" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="827" pageHeight="1169" math="0" shadow="0">
  <root>
    <mxCell id="0" />
    <mxCell id="1" parent="0" />
    <mxCell id="az-b" value="Availability Zone B (ap-northeast-2b)" style="fillColor=none;strokeColor=#147EBA;dashed=1;verticalAlign=top;fontStyle=1;fontColor=#147EBA;whiteSpace=wrap;html=1;fontSize=15;strokeWidth=2;container=0;movable=1;resizable=1;rotatable=1;deletable=1;editable=1;locked=0;connectable=1;" parent="1" vertex="1">
      <mxGeometry x="1041" y="610" width="510" height="920" as="geometry" />
    </mxCell>
    <mxCell id="JsNvUYYC68EnVG0E4_zY-1" value="Streaming Subnet 10.0.41.0/24" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=1;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_security_group;grStroke=0;strokeColor=#147EBA;fillColor=#E6F2F8;verticalAlign=top;align=left;spacingLeft=30;fontColor=#147EBA;dashed=0;" parent="1" vertex="1">
      <mxGeometry x="1050" y="1112" width="490" height="118" as="geometry" />
    </mxCell>
    <mxCell id="JsNvUYYC68EnVG0E4_zY-2" value="Notification Worker&#xa;(VPC ENI)" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#ED7100;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.lambda_function;" parent="JsNvUYYC68EnVG0E4_zY-1" vertex="1">
      <mxGeometry x="240" y="32" width="44" height="44" as="geometry" />
    </mxCell>
    <mxCell id="JsNvUYYC68EnVG0E4_zY-3" value="VPC Endpoint&#xa;(SQS Interface)" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#ED7100;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.endpoint;" parent="JsNvUYYC68EnVG0E4_zY-1" vertex="1">
      <mxGeometry x="80" y="36" width="40" height="40" as="geometry" />
    </mxCell>
    <mxCell id="region-group" value="AWS Region: ap-northeast-2 (Seoul)" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;html=1;whiteSpace=wrap;fontSize=18;fontStyle=1;container=0;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_region;strokeColor=#00A4A6;fillColor=none;verticalAlign=top;align=left;spacingLeft=30;fontColor=#147EBA;dashed=1;strokeWidth=2;" parent="1" vertex="1">
      <mxGeometry x="260" y="420" width="1430" height="1130" as="geometry" />
    </mxCell>
    <mxCell id="vpc-group" value="VPC: 10.0.0.0/16 - Ticketing System" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;html=1;whiteSpace=wrap;fontSize=16;fontStyle=1;container=0;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_vpc;strokeColor=#248814;fillColor=none;verticalAlign=top;align=left;spacingLeft=30;fontColor=#248814;dashed=0;strokeWidth=2;" parent="1" vertex="1">
      <mxGeometry x="270" y="540" width="1320" height="1000" as="geometry" />
    </mxCell>
    <mxCell id="eks-logic-group" value="EKS CLUSTER (App Layer)" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=0;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_ec2_instance_contents;strokeColor=#D86613;fillColor=none;verticalAlign=top;align=left;spacingLeft=30;fontColor=#D86613;dashed=0;" parent="1" vertex="1">
      <mxGeometry x="280" y="791" width="1290" height="309" as="geometry" />
    </mxCell>
    <mxCell id="az-a" value="Availability Zone A (ap-northeast-2a)" style="fillColor=none;strokeColor=#147EBA;dashed=1;verticalAlign=top;fontStyle=1;fontColor=#147EBA;whiteSpace=wrap;html=1;fontSize=15;strokeWidth=2;container=0;" parent="1" vertex="1">
      <mxGeometry x="329" y="610" width="510" height="920" as="geometry" />
    </mxCell>
    <mxCell id="public-subnet-a" value="Public Subnet 10.0.1.0/24" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=1;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_security_group;grStroke=0;strokeColor=#248814;fillColor=#E9F3E6;verticalAlign=top;align=left;spacingLeft=30;fontColor=#248814;dashed=0;" parent="1" vertex="1">
      <mxGeometry x="340" y="650" width="490" height="137" as="geometry" />
    </mxCell>
    <mxCell id="nat-a" value="NAT Gateway" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.nat_gateway;" parent="public-subnet-a" vertex="1">
      <mxGeometry x="60" y="47" width="42" height="42" as="geometry" />
    </mxCell>
    <mxCell id="alb-eni-a" value="ALB ENI" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.elastic_network_interface;" parent="public-subnet-a" vertex="1">
      <mxGeometry x="200" y="50" width="36" height="36" as="geometry" />
    </mxCell>
    <mxCell id="app-subnet-a" value="Private App Subnet 10.0.11.0/24" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=1;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_security_group;grStroke=0;strokeColor=#147EBA;fillColor=#E6F2F8;verticalAlign=top;align=left;spacingLeft=30;fontColor=#147EBA;dashed=0;" parent="1" vertex="1">
      <mxGeometry x="341" y="816" width="489" height="274" as="geometry" />
    </mxCell>
    <mxCell id="ingress-a" value="Ingress Controller (NGINX)&#xa;Pod AZ-A" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#ED7100;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=ing;fontSize=8;fontStyle=1" parent="app-subnet-a" vertex="1">
      <mxGeometry x="219" y="25" width="52.63" height="50" as="geometry" />
    </mxCell>
    <mxCell id="karpenter-a" value="Karpenter&#xa;Node Autoscaler" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#759C3E;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" parent="app-subnet-a" vertex="1">
      <mxGeometry x="49" y="30" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="pod-booking-a" value="Booking Service&#xa;(Lua Commit)" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" parent="app-subnet-a" vertex="1">
      <mxGeometry x="24" y="123" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="pod-auth-a" value="Auth Service" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" parent="app-subnet-a" vertex="1">
      <mxGeometry x="94" y="123" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="pod-ticket-a" value="Ticket/Inventory&#xa;(Read/Hold)" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#C71313;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" parent="app-subnet-a" vertex="1">
      <mxGeometry x="164" y="123" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="pod-payment-a" value="Payment Service" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" parent="app-subnet-a" vertex="1">
      <mxGeometry x="234" y="123" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="pod-event-a" value="Event/Show" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" parent="app-subnet-a" vertex="1">
      <mxGeometry x="301" y="123" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="pod-queue-a" value="Queue Service&#xa;(Polling/Status)" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#C925D1;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" parent="app-subnet-a" vertex="1">
      <mxGeometry x="24" y="199" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="-QtlGXSvJqmI6IdfGEf4-19" value="&lt;div&gt;&lt;span style=&quot;font-size: 10px; font-weight: 700; background-color: transparent; color: light-dark(rgb(35, 47, 62), rgb(189, 199, 212));&quot;&gt;VPC Endpoint&lt;/span&gt;&lt;/div&gt;&lt;span style=&quot;color: rgb(35, 47, 62); font-family: Helvetica; font-size: 10px; font-style: normal; font-variant-ligatures: normal; font-variant-caps: normal; font-weight: 700; letter-spacing: normal; orphans: 2; text-align: center; text-indent: 0px; text-transform: none; widows: 2; word-spacing: 0px; -webkit-text-stroke-width: 0px; white-space: nowrap; background-color: rgb(236, 236, 236); text-decoration-thickness: initial; text-decoration-style: initial; text-decoration-color: initial; display: inline !important; float: none;&quot;&gt;(Interface: ECR/CW)&lt;/span&gt;&lt;div&gt;&lt;br&gt;&lt;/div&gt;" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=12;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.endpoint;" parent="app-subnet-a" vertex="1">
      <mxGeometry x="400" y="25" width="40" height="40" as="geometry" />
    </mxCell>
    <mxCell id="-QtlGXSvJqmI6IdfGEf4-36" value="Monitoring Namespace" style="sketch=0;outlineConnect=0;fontColor=#666666;gradientColor=none;strokeColor=#666666;fillColor=none;dashed=1;verticalLabelPosition=top;verticalAlign=bottom;align=left;html=1;fontSize=10;fontStyle=1;aspect=fixed;pointerEvents=0;container=1;collapsible=0;recursiveResize=0;" parent="app-subnet-a" vertex="1">
      <mxGeometry x="354" y="115" width="120" height="72" as="geometry" />
    </mxCell>
    <mxCell id="-QtlGXSvJqmI6IdfGEf4-37" value="&lt;span style=&quot;font-weight: 400;&quot;&gt;Prometheus&lt;/span&gt;" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" parent="-QtlGXSvJqmI6IdfGEf4-36" vertex="1">
      <mxGeometry x="6" y="9" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="-QtlGXSvJqmI6IdfGEf4-38" value="&lt;span style=&quot;font-weight: 400;&quot;&gt;Grafana&lt;/span&gt;" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" parent="-QtlGXSvJqmI6IdfGEf4-36" vertex="1">
      <mxGeometry x="64" y="8" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="vpce-sqs-a" value="VPC Endpoint&#xa;(SQS Interface)" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#ED7100;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.endpoint;" parent="app-subnet-a" vertex="1">
      <mxGeometry x="139" y="35" width="40" height="40" as="geometry" />
    </mxCell>
    <mxCell id="rQ2bW2ExlWeHLL_E9Dhr-1" value="Admission Worker&lt;br&gt;(1s Loop)" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#C925D1;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" parent="app-subnet-a" vertex="1">
      <mxGeometry x="94" y="199" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="streaming-subnet-a" value="Streaming Subnet 10.0.41.0/24" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=1;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_security_group;grStroke=0;strokeColor=#147EBA;fillColor=#E6F2F8;verticalAlign=top;align=left;spacingLeft=30;fontColor=#147EBA;dashed=0;" parent="1" vertex="1">
      <mxGeometry x="340" y="1111" width="490" height="120" as="geometry" />
    </mxCell>
    <mxCell id="lambda-worker-a" value="Notification Worker&#xa;(VPC ENI)" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#ED7100;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.lambda_function;" parent="streaming-subnet-a" vertex="1">
      <mxGeometry x="240" y="32" width="44" height="44" as="geometry" />
    </mxCell>
    <mxCell id="vpce-sqs-stream-a" value="VPC Endpoint&#xa;(SQS Interface)" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#ED7100;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.endpoint;" parent="streaming-subnet-a" vertex="1">
      <mxGeometry x="80" y="36" width="40" height="40" as="geometry" />
    </mxCell>
    <mxCell id="cache-subnet-a" value="Cache Subnet 10.0.31.0/24" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=1;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_security_group;grStroke=0;strokeColor=#DD344C;fillColor=#FCE9E9;verticalAlign=top;align=left;spacingLeft=30;fontColor=#DD344C;dashed=0;" parent="1" vertex="1">
      <mxGeometry x="340" y="1241" width="490" height="130" as="geometry" />
    </mxCell>
    <mxCell id="redis-primary" value="Redis Cluster (Queue + Active + Inventory + Idempotency)&#xa;wait:{key} | active:{key} | active_ttl:{req} | inv:{key}" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#C925D1;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.elasticache_for_redis;" parent="cache-subnet-a" vertex="1">
      <mxGeometry x="220" y="41" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="db-subnet-a" value="Database Subnet 10.0.21.0/24" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=1;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_security_group;grStroke=0;strokeColor=#3F8624;fillColor=#E6F6E4;verticalAlign=top;align=left;spacingLeft=30;fontColor=#3F8624;dashed=0;" parent="1" vertex="1">
      <mxGeometry x="341" y="1378" width="489" height="142" as="geometry" />
    </mxCell>
    <mxCell id="rds-primary" value="RDS PostgreSQL&#xa;Primary" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#C925D1;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.rds;" parent="db-subnet-a" vertex="1">
      <mxGeometry x="220" y="50" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="public-subnet-b" value="Public Subnet 10.0.2.0/24" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=1;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_security_group;grStroke=0;strokeColor=#248814;fillColor=#E9F3E6;verticalAlign=top;align=left;spacingLeft=30;fontColor=#248814;dashed=0;" parent="1" vertex="1">
      <mxGeometry x="1055" y="652" width="485" height="137" as="geometry" />
    </mxCell>
    <mxCell id="nat-b" value="NAT Gateway" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.nat_gateway;" parent="public-subnet-b" vertex="1">
      <mxGeometry x="60" y="47" width="42" height="42" as="geometry" />
    </mxCell>
    <mxCell id="alb-eni-b" value="ALB ENI" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.elastic_network_interface;" parent="public-subnet-b" vertex="1">
      <mxGeometry x="200" y="50" width="36" height="36" as="geometry" />
    </mxCell>
    <mxCell id="app-subnet-b" value="Private App&lt;span style=&quot;background-color: transparent; color: light-dark(rgb(20, 126, 186), rgb(69, 160, 212));&quot;&gt;&amp;nbsp;Subnet 10.0.12.0/24&lt;/span&gt;" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=1;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_security_group;grStroke=0;strokeColor=#147EBA;fillColor=#E6F2F8;verticalAlign=top;align=left;spacingLeft=30;fontColor=#147EBA;dashed=0;" parent="1" vertex="1">
      <mxGeometry x="1055" y="810" width="485" height="280" as="geometry" />
    </mxCell>
    <mxCell id="karpenter-b" value="Karpenter&#xa;Node Autoscaler" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#759C3E;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" parent="app-subnet-b" vertex="1">
      <mxGeometry x="62" y="25" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="-QtlGXSvJqmI6IdfGEf4-14" value="&lt;div&gt;&lt;span style=&quot;font-size: 10px; font-weight: 700; background-color: transparent; color: light-dark(rgb(35, 47, 62), rgb(189, 199, 212));&quot;&gt;VPC Endpoint&lt;/span&gt;&lt;/div&gt;&lt;span style=&quot;color: rgb(35, 47, 62); font-family: Helvetica; font-size: 10px; font-style: normal; font-variant-ligatures: normal; font-variant-caps: normal; font-weight: 700; letter-spacing: normal; orphans: 2; text-align: center; text-indent: 0px; text-transform: none; widows: 2; word-spacing: 0px; -webkit-text-stroke-width: 0px; white-space: nowrap; background-color: rgb(236, 236, 236); text-decoration-thickness: initial; text-decoration-style: initial; text-decoration-color: initial; display: inline !important; float: none;&quot;&gt;(Interface: ECR/CW)&lt;/span&gt;&lt;div&gt;&lt;br&gt;&lt;/div&gt;" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=12;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.endpoint;" parent="app-subnet-b" vertex="1">
      <mxGeometry x="379" y="28" width="40" height="40" as="geometry" />
    </mxCell>
    <mxCell id="Gj3veLgzFGyr1Cg06jAb-2" value="Ingress Controller" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#ED7100;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=ing;fontSize=8;fontStyle=1" parent="app-subnet-b" vertex="1">
      <mxGeometry x="215" y="25" width="52.63" height="50" as="geometry" />
    </mxCell>
    <mxCell id="vpce-sqs-b" value="VPC Endpoint&#xa;(SQS Interface)" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#ED7100;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.endpoint;" parent="app-subnet-b" vertex="1">
      <mxGeometry x="146" y="35" width="40" height="40" as="geometry" />
    </mxCell>
    <mxCell id="pod-queue-b" value="Queue Service&#xa;(Polling/Status)" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#C925D1;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" parent="app-subnet-b" vertex="1">
      <mxGeometry x="31" y="201" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="rQ2bW2ExlWeHLL_E9Dhr-2" value="Admission Worker&lt;br&gt;(1s Loop)" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#C925D1;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" parent="app-subnet-b" vertex="1">
      <mxGeometry x="104" y="201" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="cache-subnet-b" value="Cache Subnet 10.0.32.0/24" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=1;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_security_group;grStroke=0;strokeColor=#DD344C;fillColor=#FCE9E9;verticalAlign=top;align=left;spacingLeft=30;fontColor=#DD344C;dashed=0;" parent="1" vertex="1">
      <mxGeometry x="1051" y="1239" width="489" height="131" as="geometry" />
    </mxCell>
    <mxCell id="redis-replica" value="Redis Replica (AZ-B)&#xa;Auto-Failover" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#C925D1;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.elasticache_for_redis;" parent="cache-subnet-b" vertex="1">
      <mxGeometry x="206" y="33" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="db-subnet-b" value="Database Subnet 10.0.22.0/24" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=1;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_security_group;grStroke=0;strokeColor=#3F8624;fillColor=#E6F6E4;verticalAlign=top;align=left;spacingLeft=30;fontColor=#3F8624;dashed=0;" parent="1" vertex="1">
      <mxGeometry x="1050" y="1377" width="490" height="143" as="geometry" />
    </mxCell>
    <mxCell id="rds-standby" value="RDS PostgreSQL&#xa;Standby" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#C925D1;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.rds;" parent="db-subnet-b" vertex="1">
      <mxGeometry x="206" y="50" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="rds-proxy" value="RDS Proxy&#xa;(Connection Pooling)" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#C925D1;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=11;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.rds_proxy;" parent="1" vertex="1">
      <mxGeometry x="906" y="1333" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="users" value="Users" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;strokeColor=#232F3E;fillColor=#ffffff;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=12;fontStyle=0;aspect=fixed;shape=mxgraph.aws4.resourceIcon;resIcon=mxgraph.aws4.users;" parent="1" vertex="1">
      <mxGeometry x="554" y="340" width="60" height="60" as="geometry" />
    </mxCell>
    <mxCell id="route53" value="Route 53" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=11;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.route_53;" parent="1" vertex="1">
      <mxGeometry x="690" y="340" width="60" height="60" as="geometry" />
    </mxCell>
    <mxCell id="cloudfront" value="CloudFront CDN" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=11;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.cloudfront;" parent="1" vertex="1">
      <mxGeometry x="828" y="340" width="60" height="60" as="geometry" />
    </mxCell>
    <mxCell id="waf" value="AWS WAF&#xa;(Attached)" style="sketch=0;outlineConnect=0;fontColor=#DD344C;gradientColor=none;fillColor=#DD344C;strokeColor=none;dashed=0;verticalLabelPosition=top;verticalAlign=bottom;align=center;html=1;fontSize=10;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.waf;" parent="1" vertex="1">
      <mxGeometry x="838" y="300" width="40" height="40" as="geometry" />
    </mxCell>
    <mxCell id="alb" value="ALB" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=11;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.application_load_balancer;" parent="1" vertex="1">
      <mxGeometry x="920" y="626.5" width="60" height="60" as="geometry" />
    </mxCell>
    <mxCell id="igw" value="Internet Gateway" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=11;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.internet_gateway;" parent="1" vertex="1">
      <mxGeometry x="837" y="504" width="60" height="60" as="geometry" />
    </mxCell>
    <mxCell id="eks-cp" value="EKS Control Plane" style="sketch=0;outlineConnect=0;fontColor=#232F3E;fillColor=#ED7100;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=11;fontStyle=1;aspect=fixed;shape=mxgraph.aws4.eks_cloud;" parent="1" vertex="1">
      <mxGeometry x="1610" y="686.5" width="68" height="68" as="geometry" />
    </mxCell>
    <mxCell id="cloudwatch" value="CloudWatch" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#759C3E;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.cloudwatch;" parent="1" vertex="1">
      <mxGeometry x="1606" y="842" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="cloudtrail" value="CloudTrail" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#759C3E;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.cloudtrail;" parent="1" vertex="1">
      <mxGeometry x="1606" y="930" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="s3" value="S3 Assets" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#7AA116;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.s3;" parent="1" vertex="1">
      <mxGeometry x="1607" y="1010" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="secrets-manager" value="Secrets Manager" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#DD344C;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.secrets_manager;" parent="1" vertex="1">
      <mxGeometry x="1606" y="1090" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="link-user-dns" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#232F3E;strokeWidth=2.5;endArrow=classic;endFill=1;" parent="1" source="users" target="route53" edge="1">
      <mxGeometry relative="1" as="geometry" />
    </mxCell>
    <mxCell id="link-dns-cdn" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#232F3E;strokeWidth=2.5;endArrow=classic;endFill=1;" parent="1" source="route53" target="cloudfront" edge="1">
      <mxGeometry relative="1" as="geometry" />
    </mxCell>
    <mxCell id="link-alb-ingress-a" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#8C4FFF;strokeWidth=2.5;endArrow=classic;endFill=1;fontSize=10;" parent="1" source="alb" target="ingress-a" edge="1">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="950" y="800" />
          <mxPoint x="590" y="800" />
        </Array>
      </mxGeometry>
    </mxCell>
    <mxCell id="link-app-a-proxy" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#147EBA;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;" parent="1" source="pod-booking-a" target="rds-proxy" edge="1">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="396" y="1370" />
        </Array>
      </mxGeometry>
    </mxCell>
    <mxCell id="link-proxy-db" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#C925D1;strokeWidth=2;endArrow=classic;endFill=1;" parent="1" source="rds-proxy" target="rds-primary" edge="1">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="810" y="1360" />
          <mxPoint x="810" y="1360" />
        </Array>
      </mxGeometry>
    </mxCell>
    <mxCell id="link-karpenter-a-cp" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#ED7100;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;" parent="1" source="karpenter-a" target="eks-cp" edge="1">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="420" y="720" />
        </Array>
      </mxGeometry>
    </mxCell>
    <mxCell id="waiting-room-group" value="Waiting &amp; Gatekeeping" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=1;container=0;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_aws_cloud_alt;strokeColor=#D86613;fillColor=none;verticalAlign=top;align=left;spacingLeft=30;fontColor=#D86613;dashed=1;" parent="1" vertex="1">
      <mxGeometry x="848" y="434" width="210" height="70" as="geometry" />
    </mxCell>
    <mxCell id="waiting-room-lambda" value="Lambda@Edge&#xa;(Check Token)" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#ED7100;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.lambda_function;" parent="1" vertex="1">
      <mxGeometry x="932.5" y="465" width="35" height="35" as="geometry" />
    </mxCell>
    <mxCell id="link-cdn-waiting" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#8C4FFF;strokeWidth=3;endArrow=classic;endFill=1;fontSize=10;" parent="1" source="cloudfront" target="waiting-room-lambda" edge="1">
      <mxGeometry relative="1" as="geometry">
        <mxPoint x="950" y="455" as="targetPoint" />
      </mxGeometry>
    </mxCell>
    <mxCell id="-QtlGXSvJqmI6IdfGEf4-23" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#7AA116;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;" parent="1" source="-QtlGXSvJqmI6IdfGEf4-19" target="cloudwatch" edge="1">
      <mxGeometry relative="1" as="geometry">
        <mxPoint x="781" y="860" as="sourcePoint" />
        <mxPoint x="1606" y="860" as="targetPoint" />
        <Array as="points">
          <mxPoint x="1420" y="860" />
          <mxPoint x="1420" y="860" />
        </Array>
      </mxGeometry>
    </mxCell>
    <mxCell id="-QtlGXSvJqmI6IdfGEf4-39" value="Booking Service" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" parent="1" vertex="1">
      <mxGeometry x="1084" y="933" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="-QtlGXSvJqmI6IdfGEf4-40" value="Auth Service" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" parent="1" vertex="1">
      <mxGeometry x="1154" y="933" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="-QtlGXSvJqmI6IdfGEf4-41" value="Ticket/Inventory" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#C71313;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" parent="1" vertex="1">
      <mxGeometry x="1224" y="933" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="-QtlGXSvJqmI6IdfGEf4-42" value="Payment Service" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" parent="1" vertex="1">
      <mxGeometry x="1294" y="933" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="-QtlGXSvJqmI6IdfGEf4-43" value="Event/Show" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" parent="1" vertex="1">
      <mxGeometry x="1361" y="933" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="-QtlGXSvJqmI6IdfGEf4-44" value="Monitoring Namespace" style="sketch=0;outlineConnect=0;fontColor=#666666;gradientColor=none;strokeColor=#666666;fillColor=none;dashed=1;verticalLabelPosition=top;verticalAlign=bottom;align=left;html=1;fontSize=10;fontStyle=1;aspect=fixed;pointerEvents=0;container=1;collapsible=0;recursiveResize=0;" parent="1" vertex="1">
      <mxGeometry x="1414" y="925" width="120" height="72" as="geometry" />
    </mxCell>
    <mxCell id="-QtlGXSvJqmI6IdfGEf4-45" value="&lt;span style=&quot;font-weight: 400;&quot;&gt;Prometheus&lt;/span&gt;" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" parent="-QtlGXSvJqmI6IdfGEf4-44" vertex="1">
      <mxGeometry x="6" y="9" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="-QtlGXSvJqmI6IdfGEf4-46" value="&lt;span style=&quot;font-weight: 400;&quot;&gt;Grafana&lt;/span&gt;" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" parent="-QtlGXSvJqmI6IdfGEf4-44" vertex="1">
      <mxGeometry x="64" y="8" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="link-cdn-s3" value="Static Content&#xa;(Origin Group)" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#8C4FFF;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;fontSize=10;fontColor=#8C4FFF;" parent="1" source="cloudfront" target="s3" edge="1">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="1560" y="370" />
          <mxPoint x="1560" y="1034" />
        </Array>
      </mxGeometry>
    </mxCell>
    <mxCell id="-QtlGXSvJqmI6IdfGEf4-51" value="&lt;div&gt;&lt;span style=&quot;font-size: 10px; font-weight: 700;&quot;&gt;VPC Endpoint&lt;/span&gt;&lt;br style=&quot;font-size: 10px; font-weight: 700;&quot;&gt;&lt;span style=&quot;font-size: 10px; font-weight: 700;&quot;&gt;(Gateway: S3/Dynamo)&lt;/span&gt;&lt;/div&gt;&lt;div&gt;&lt;span style=&quot;font-size: 10px; font-weight: 700;&quot;&gt;&lt;br&gt;&lt;/span&gt;&lt;/div&gt;" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=12;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.endpoint;" parent="1" vertex="1">
      <mxGeometry x="980" y="511" width="46" height="46" as="geometry" />
    </mxCell>
    <mxCell id="-QtlGXSvJqmI6IdfGEf4-52" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#7AA116;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;" parent="1" source="-QtlGXSvJqmI6IdfGEf4-51" target="s3" edge="1">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="1003" y="590" />
          <mxPoint x="1500" y="590" />
          <mxPoint x="1500" y="1034" />
        </Array>
      </mxGeometry>
    </mxCell>
    <mxCell id="link-ticket-redis" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#FF3333;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;" parent="1" source="pod-ticket-a" target="redis-primary" edge="1">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="530" y="1170" />
          <mxPoint x="584" y="1170" />
        </Array>
      </mxGeometry>
    </mxCell>
    <mxCell id="link-ticket-rds" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#FF3333;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;" parent="1" target="rds-proxy" edge="1">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="531" y="927" />
          <mxPoint x="931" y="927" />
        </Array>
        <mxPoint x="531" y="940" as="sourcePoint" />
        <mxPoint x="931" y="1303.0606460718918" as="targetPoint" />
      </mxGeometry>
    </mxCell>
    <mxCell id="sqs-icon" value="SQS Queue&#xa;(Standard/FIFO)" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#E7157B;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.sqs;" parent="1" vertex="1">
      <mxGeometry x="1596" y="1253" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="link-booking-sqs" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#E7157B;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;" parent="1" source="vpce-sqs-a" target="sqs-icon" edge="1">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="500" y="1460" />
          <mxPoint x="1620" y="1460" />
        </Array>
      </mxGeometry>
    </mxCell>
    <mxCell id="link-sqs-worker" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#E7157B;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;" parent="1" source="sqs-icon" target="vpce-sqs-stream-a" edge="1">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="440" y="1280" />
        </Array>
      </mxGeometry>
    </mxCell>
    <mxCell id="link-queue-redis-a" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#FF3333;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;" parent="1" source="pod-queue-a" target="redis-primary" edge="1">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="389" y="1170" />
          <mxPoint x="584" y="1170" />
        </Array>
      </mxGeometry>
    </mxCell>
    <mxCell id="link-scheduler-redis-a" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#FF3333;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;exitX=0.48;exitY=1;exitDx=0;exitDy=0;exitPerimeter=0;" parent="1" source="rQ2bW2ExlWeHLL_E9Dhr-1" target="redis-primary" edge="1">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="459" y="1170" />
          <mxPoint x="584" y="1170" />
        </Array>
        <mxPoint x="459" y="1050" as="sourcePoint" />
      </mxGeometry>
    </mxCell>
    <mxCell id="link-booking-redis-commit" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#FF3333;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;" parent="1" source="pod-booking-a" target="redis-primary" edge="1">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="300" y="1170" />
          <mxPoint x="584" y="1170" />
        </Array>
      </mxGeometry>
    </mxCell>
    <mxCell id="link-lambda-alb" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#8C4FFF;strokeWidth=3;endArrow=classic;endFill=1;fontSize=10;" parent="1" source="waiting-room-lambda" target="alb" edge="1">
      <mxGeometry relative="1" as="geometry">
        <mxPoint x="930" y="620" as="targetPoint" />
      </mxGeometry>
    </mxCell>
  </root>
</mxGraphModel>
