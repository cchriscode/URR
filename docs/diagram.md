<mxGraphModel dx="2305" dy="1235" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="827" pageHeight="1169" math="0" shadow="0">
  <root>
    <mxCell id="0" />
    <mxCell id="1" parent="0" />
    <mxCell id="region-group" value="AWS Region: ap-northeast-2 (Seoul)" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;html=1;whiteSpace=wrap;fontSize=18;fontStyle=1;container=0;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_region;strokeColor=#00A4A6;fillColor=none;verticalAlign=top;align=left;spacingLeft=30;fontColor=#147EBA;dashed=1;strokeWidth=2;" vertex="1" parent="1">
      <mxGeometry x="260" y="410" width="1550" height="1215" as="geometry" />
    </mxCell>
    <mxCell id="vpc-group" value="VPC: 10.0.0.0/16 - URR Ticketing System" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;html=1;whiteSpace=wrap;fontSize=16;fontStyle=1;container=0;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_vpc;strokeColor=#248814;fillColor=none;verticalAlign=top;align=left;spacingLeft=30;fontColor=#248814;dashed=0;strokeWidth=2;" vertex="1" parent="1">
      <mxGeometry x="270" y="533" width="1330" height="1082" as="geometry" />
    </mxCell>
    <mxCell id="eks-logic-group" value="EKS CLUSTER (Private App Layer) + Karpenter Auto-Scaling" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=0;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_ec2_instance_contents;strokeColor=#D86613;fillColor=none;verticalAlign=top;align=left;spacingLeft=30;fontColor=#D86613;dashed=0;" vertex="1" parent="1">
      <mxGeometry x="280" y="791" width="1290" height="313" as="geometry" />
    </mxCell>
    <mxCell id="msk-cluster-layer-final" value="MSK CLUSTER (Private Streaming Layer)" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=0;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_aws_cloud_alt;strokeColor=#8C4FFF;fillColor=none;verticalAlign=top;align=left;spacingLeft=30;fontColor=#8C4FFF;dashed=1;" vertex="1" parent="1">
      <mxGeometry x="280" y="1110" width="1290" height="150" as="geometry" />
    </mxCell>
    <mxCell id="az-a" value="Availability Zone A (ap-northeast-2a)" style="fillColor=none;strokeColor=#147EBA;dashed=1;verticalAlign=top;fontStyle=1;fontColor=#147EBA;whiteSpace=wrap;html=1;fontSize=15;strokeWidth=2;container=0;" vertex="1" parent="1">
      <mxGeometry x="329" y="610" width="510" height="995" as="geometry" />
    </mxCell>
    <mxCell id="az-c" value="Availability Zone C (ap-northeast-2c)" style="fillColor=none;strokeColor=#147EBA;dashed=1;verticalAlign=top;fontStyle=1;fontColor=#147EBA;whiteSpace=wrap;html=1;fontSize=15;strokeWidth=2;container=0;" vertex="1" parent="1">
      <mxGeometry x="1040" y="610" width="510" height="995" as="geometry" />
    </mxCell>
    <mxCell id="public-subnet-a" value="Public Subnet 10.0.0.0/24" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=1;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_security_group;grStroke=0;strokeColor=#248814;fillColor=#E9F3E6;verticalAlign=top;align=left;spacingLeft=30;fontColor=#248814;dashed=0;" vertex="1" parent="1">
      <mxGeometry x="340" y="650" width="300" height="137" as="geometry" />
    </mxCell>
    <mxCell id="nat-a" value="NAT Gateway" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.nat_gateway;" vertex="1" parent="public-subnet-a">
      <mxGeometry x="60" y="47" width="42" height="42" as="geometry" />
    </mxCell>
    <mxCell id="alb-eni-a" value="ALB ENI" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.elastic_network_interface;" vertex="1" parent="public-subnet-a">
      <mxGeometry x="200" y="50" width="36" height="36" as="geometry" />
    </mxCell>
    <mxCell id="public-subnet-c" value="Public Subnet 10.0.1.0/24" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=1;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_security_group;grStroke=0;strokeColor=#248814;fillColor=#E9F3E6;verticalAlign=top;align=left;spacingLeft=30;fontColor=#248814;dashed=0;" vertex="1" parent="1">
      <mxGeometry x="1055" y="650" width="295" height="137" as="geometry" />
    </mxCell>
    <mxCell id="nat-c" value="NAT Gateway" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.nat_gateway;" vertex="1" parent="public-subnet-c">
      <mxGeometry x="60" y="47" width="42" height="42" as="geometry" />
    </mxCell>
    <mxCell id="alb-eni-c" value="ALB ENI" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.elastic_network_interface;" vertex="1" parent="public-subnet-c">
      <mxGeometry x="200" y="50" width="36" height="36" as="geometry" />
    </mxCell>
    <mxCell id="app-subnet-a" value="Private App Subnet (NAT) 10.0.10.0/24" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=1;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_security_group;grStroke=0;strokeColor=#147EBA;fillColor=#E6F2F8;verticalAlign=top;align=left;spacingLeft=30;fontColor=#147EBA;dashed=0;" vertex="1" parent="1">
      <mxGeometry x="341" y="816" width="490" height="280" as="geometry" />
    </mxCell>
    <mxCell id="pod-gateway-a" value="gateway-service" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#ED7100;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" vertex="1" parent="app-subnet-a">
      <mxGeometry x="229" y="25" width="46.88" height="45" as="geometry" />
    </mxCell>
    <mxCell id="vpce-if-a" value="VPC Endpoint&#xa;(Interface x9)" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.endpoint;" vertex="1" parent="app-subnet-a">
      <mxGeometry x="400" y="25" width="40" height="40" as="geometry" />
    </mxCell>
    <mxCell id="mon-ns-a" value="Monitoring Namespace" style="sketch=0;outlineConnect=0;fontColor=#666666;gradientColor=none;strokeColor=#666666;fillColor=none;dashed=1;verticalLabelPosition=top;verticalAlign=bottom;align=left;html=1;fontSize=10;fontStyle=1;pointerEvents=0;container=1;collapsible=0;recursiveResize=0;" vertex="1" parent="app-subnet-a">
      <mxGeometry x="14" y="200" width="460" height="72" as="geometry" />
    </mxCell>
    <mxCell id="mon-prom" value="Prometheus" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=8;fontStyle=0" vertex="1" parent="mon-ns-a">
      <mxGeometry x="10" y="9" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="mon-grafana" value="Grafana" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=8;fontStyle=0" vertex="1" parent="mon-ns-a">
      <mxGeometry x="100" y="9" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="mon-loki" value="Loki" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=8;fontStyle=0" vertex="1" parent="mon-ns-a">
      <mxGeometry x="190" y="9" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="mon-zipkin" value="Zipkin" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=8;fontStyle=0" vertex="1" parent="mon-ns-a">
      <mxGeometry x="280" y="9" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="mon-alertmgr" value="AlertManager" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=8;fontStyle=0" vertex="1" parent="mon-ns-a">
      <mxGeometry x="370" y="9" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="pod-community-c" value="community&lt;div&gt;&lt;br&gt;&lt;/div&gt;" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" vertex="1" parent="app-subnet-a">
      <mxGeometry x="390" y="109" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="9XcHtQANKYaBjinsaZiJ-1" value="EKS worker Nodes" style="sketch=0;outlineConnect=0;fontColor=#666666;gradientColor=none;strokeColor=#666666;fillColor=none;dashed=1;verticalLabelPosition=top;verticalAlign=bottom;align=left;html=1;fontSize=10;fontStyle=1;pointerEvents=0;container=1;collapsible=0;recursiveResize=0;" vertex="1" parent="app-subnet-a">
      <mxGeometry x="14" y="109" width="436" height="72" as="geometry" />
    </mxCell>
    <mxCell id="pod-auth-a" value="auth" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" vertex="1" parent="9XcHtQANKYaBjinsaZiJ-1">
      <mxGeometry x="75" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="pod-payment-a" value="payment" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#C71313;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" vertex="1" parent="9XcHtQANKYaBjinsaZiJ-1">
      <mxGeometry x="135" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="pod-stats-c" value="stats" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" vertex="1" parent="9XcHtQANKYaBjinsaZiJ-1">
      <mxGeometry x="255" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="pod-queue-a" value="queue" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#C71313;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" vertex="1" parent="9XcHtQANKYaBjinsaZiJ-1">
      <mxGeometry x="195" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="pod-catalog-c" value="catalog" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" vertex="1" parent="9XcHtQANKYaBjinsaZiJ-1">
      <mxGeometry x="315" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="pod-ticket-a" value="ticket" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#C71313;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" vertex="1" parent="9XcHtQANKYaBjinsaZiJ-1">
      <mxGeometry x="9" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="pod-frontend-a" value="frontend" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" vertex="1" parent="9XcHtQANKYaBjinsaZiJ-1">
      <mxGeometry x="375" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="9XcHtQANKYaBjinsaZiJ-39" value="Karpenter&#xa;Node Autoscaler" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#759C3E;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" vertex="1" parent="app-subnet-a">
      <mxGeometry x="48" y="23" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="app-subnet-c" value="Private App Subnet (NAT) 10.0.11.0/24" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=1;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_security_group;grStroke=0;strokeColor=#147EBA;fillColor=#E6F2F8;verticalAlign=top;align=left;spacingLeft=30;fontColor=#147EBA;dashed=0;" vertex="1" parent="1">
      <mxGeometry x="1055" y="816" width="485" height="280" as="geometry" />
    </mxCell>
    <mxCell id="9XcHtQANKYaBjinsaZiJ-40" value="Karpenter&#xa;Node Autoscaler" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#759C3E;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" vertex="1" parent="app-subnet-c">
      <mxGeometry x="45" y="22" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="streaming-subnet-a" value="Private Streaming Subnet (NAT) 10.0.40.0/24" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=1;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_security_group;grStroke=0;strokeColor=#147EBA;fillColor=#E6F2F8;verticalAlign=top;align=left;spacingLeft=30;fontColor=#147EBA;dashed=0;" vertex="1" parent="1">
      <mxGeometry x="340" y="1131" width="490" height="120" as="geometry" />
    </mxCell>
    <mxCell id="broker-eni-a" value="MSK Broker ENI" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.elastic_network_interface;" vertex="1" parent="streaming-subnet-a">
      <mxGeometry x="60" y="36" width="36" height="36" as="geometry" />
    </mxCell>
    <mxCell id="sqs-a" value="SQS FIFO&#xa;ticket-events" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#E7157B;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.sqs;" vertex="1" parent="streaming-subnet-a">
      <mxGeometry x="185" y="30" width="44" height="44" as="geometry" />
    </mxCell>
    <mxCell id="ticket-worker-a" value="Ticket Worker&#xa;(VPC ENI)" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#ED7100;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.lambda_function;" vertex="1" parent="streaming-subnet-a">
      <mxGeometry x="330" y="30" width="44" height="44" as="geometry" />
    </mxCell>
    <mxCell id="streaming-subnet-c" value="Private Streaming Subnet (NAT) 10.0.41.0/24" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=1;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_security_group;grStroke=0;strokeColor=#147EBA;fillColor=#E6F2F8;verticalAlign=top;align=left;spacingLeft=30;fontColor=#147EBA;dashed=0;" vertex="1" parent="1">
      <mxGeometry x="1055" y="1129" width="485" height="120" as="geometry" />
    </mxCell>
    <mxCell id="broker-eni-c" value="MSK Broker ENI" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.elastic_network_interface;" vertex="1" parent="streaming-subnet-c">
      <mxGeometry x="79" y="38" width="36" height="36" as="geometry" />
    </mxCell>
    <mxCell id="cache-subnet-a" value="Private Cache Subnet (Isolated) 10.0.30.0/24" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=1;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_security_group;grStroke=0;strokeColor=#DD344C;fillColor=#FCE9E9;verticalAlign=top;align=left;spacingLeft=30;fontColor=#DD344C;dashed=0;" vertex="1" parent="1">
      <mxGeometry x="340" y="1264" width="490" height="120" as="geometry" />
    </mxCell>
    <mxCell id="redis-primary" value="ElastiCache Redis&#xa;Primary (r6g.large 13GB)" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#C925D1;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.elasticache_for_redis;" vertex="1" parent="cache-subnet-a">
      <mxGeometry x="220" y="36" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="cache-subnet-c" value="Private Cache Subnet (Isolated) 10.0.31.0/24" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=1;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_security_group;grStroke=0;strokeColor=#DD344C;fillColor=#FCE9E9;verticalAlign=top;align=left;spacingLeft=30;fontColor=#DD344C;dashed=0;" vertex="1" parent="1">
      <mxGeometry x="1055" y="1263" width="485" height="127" as="geometry" />
    </mxCell>
    <mxCell id="redis-replica" value="ElastiCache Redis&#xa;Replica (r6g.large 13GB)&#xa;Auto-Failover" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#C925D1;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.elasticache_for_redis;" vertex="1" parent="cache-subnet-c">
      <mxGeometry x="206" y="36" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="db-subnet-a" value="Private DB Subnet (Isolated) 10.0.20.0/24" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=0;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_security_group;grStroke=0;strokeColor=#3F8624;fillColor=#E6F6E4;verticalAlign=top;align=left;spacingLeft=30;fontColor=#3F8624;dashed=0;movable=1;resizable=1;rotatable=1;deletable=1;editable=1;locked=0;connectable=1;" vertex="1" parent="1">
      <mxGeometry x="341" y="1401" width="489" height="180" as="geometry" />
    </mxCell>
    <mxCell id="db-subnet-c" value="Private DB Subnet (Isolated) 10.0.21.0/24" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=0;container=1;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_security_group;grStroke=0;strokeColor=#3F8624;fillColor=#E6F6E4;verticalAlign=top;align=left;spacingLeft=30;fontColor=#3F8624;dashed=0;movable=1;resizable=1;rotatable=1;deletable=1;editable=1;locked=0;connectable=1;" vertex="1" parent="1">
      <mxGeometry x="1055" y="1401" width="459" height="180" as="geometry" />
    </mxCell>
    <mxCell id="rds-standby" value="RDS PostgreSQL&lt;br&gt;Standby" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#C925D1;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.rds;movable=1;resizable=1;rotatable=1;deletable=1;editable=1;locked=0;connectable=1;" vertex="1" parent="db-subnet-c">
      <mxGeometry x="100" y="66" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="rds-proxy" value="RDS Proxy&#xa;(Connection Pooling)" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#C925D1;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=11;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.rds_proxy;" vertex="1" parent="1">
      <mxGeometry x="907" y="1403" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="msk-central" value="MSK Kafka Cluster&#xa;(Multi-AZ)" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#E7157B;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.managed_streaming_for_kafka;" vertex="1" parent="1">
      <mxGeometry x="897" y="1161" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="users" value="Users" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;strokeColor=#232F3E;fillColor=#ffffff;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=12;fontStyle=0;aspect=fixed;shape=mxgraph.aws4.resourceIcon;resIcon=mxgraph.aws4.users;" vertex="1" parent="1">
      <mxGeometry x="621" y="263" width="60" height="60" as="geometry" />
    </mxCell>
    <mxCell id="route53" value="Route 53" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=11;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.route_53;" vertex="1" parent="1">
      <mxGeometry x="757" y="263" width="60" height="60" as="geometry" />
    </mxCell>
    <mxCell id="cloudfront" value="CloudFront CDN" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=11;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.cloudfront;" vertex="1" parent="1">
      <mxGeometry x="895" y="263" width="60" height="60" as="geometry" />
    </mxCell>
    <mxCell id="waf" value="AWS WAF&#xa;(Attached)" style="sketch=0;outlineConnect=0;fontColor=#DD344C;gradientColor=none;fillColor=#DD344C;strokeColor=none;dashed=0;verticalLabelPosition=top;verticalAlign=bottom;align=center;html=1;fontSize=10;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.waf;" vertex="1" parent="1">
      <mxGeometry x="905" y="223" width="40" height="40" as="geometry" />
    </mxCell>
    <mxCell id="alb" value="ALB" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=11;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.application_load_balancer;" vertex="1" parent="1">
      <mxGeometry x="895" y="620" width="60" height="60" as="geometry" />
    </mxCell>
    <mxCell id="waiting-room-group" value="Virtual Waiting Room (VWR Tier 1)" style="points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];outlineConnect=0;gradientColor=none;html=1;whiteSpace=wrap;fontSize=12;fontStyle=1;container=0;pointerEvents=0;collapsible=0;recursiveResize=0;shape=mxgraph.aws4.group;grIcon=mxgraph.aws4.group_aws_cloud_alt;strokeColor=#D86613;fillColor=none;verticalAlign=top;align=left;spacingLeft=30;fontColor=#D86613;dashed=1;" vertex="1" parent="1">
      <mxGeometry x="830" y="418" width="350" height="82" as="geometry" />
    </mxCell>
    <mxCell id="api-gw-vwr" value="API Gateway&#xa;VWR API" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#E7157B;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.api_gateway;" vertex="1" parent="1">
      <mxGeometry x="996" y="440" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="vpce-gw" value="VPC Endpoint&#xa;(Gateway: S3)" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.endpoint;" vertex="1" parent="1">
      <mxGeometry x="990" y="511.5" width="52.5" height="52.5" as="geometry" />
    </mxCell>
    <mxCell id="eks-cp" value="EKS Control Plane&#xa;(Private Only)" style="sketch=0;outlineConnect=0;fontColor=#232F3E;fillColor=#ED7100;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=11;fontStyle=1;aspect=fixed;shape=mxgraph.aws4.eks_cloud;" vertex="1" parent="1">
      <mxGeometry x="1620" y="680" width="68" height="68" as="geometry" />
    </mxCell>
    <mxCell id="ecr" value="ECR" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#ED7100;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.ecr;" vertex="1" parent="1">
      <mxGeometry x="1610" y="1096" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="argocd" value="ArgoCD&#xa;(GitOps)" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#EF7B4D;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.resourceIcon;resIcon=mxgraph.aws4.codedeploy;" vertex="1" parent="1">
      <mxGeometry x="1820" y="690" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="cloudwatch" value="CloudWatch" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#759C3E;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.cloudwatch;" vertex="1" parent="1">
      <mxGeometry x="1606" y="860" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="s3" value="S3 Assets&#xa;(OAC)" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#7AA116;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.s3;" vertex="1" parent="1">
      <mxGeometry x="1607" y="930" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="secrets-manager" value="Secrets Manager" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#DD344C;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.secrets_manager;" vertex="1" parent="1">
      <mxGeometry x="1606" y="1020" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="dynamodb" value="DynamoDB&#xa;(VWR Queue)" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#2E73B8;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.dynamodb;" vertex="1" parent="1">
      <mxGeometry x="1610" y="1180" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="link-user-dns" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#232F3E;strokeWidth=2.5;endArrow=classic;endFill=1;" edge="1" parent="1" source="users" target="route53">
      <mxGeometry relative="1" as="geometry" />
    </mxCell>
    <mxCell id="link-dns-cdn" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#232F3E;strokeWidth=2.5;endArrow=classic;endFill=1;" edge="1" parent="1" source="route53" target="cloudfront">
      <mxGeometry relative="1" as="geometry" />
    </mxCell>
    <mxCell id="link-cdn-edge" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#8C4FFF;strokeWidth=3;endArrow=classic;endFill=1;fontSize=10;" edge="1" parent="1" source="cloudfront" target="edge-lambda">
      <mxGeometry relative="1" as="geometry">
        <mxPoint x="886.5" y="370" as="targetPoint" />
      </mxGeometry>
    </mxCell>
    <mxCell id="link-alb-frontend" value=":3000" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#2875E2;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;fontSize=9;fontColor=#2875E2;" edge="1" parent="1" source="alb" target="pod-frontend-a">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="955" y="810" />
          <mxPoint x="755" y="810" />
        </Array>
      </mxGeometry>
    </mxCell>
    <mxCell id="link-alb-gateway" value="" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#8C4FFF;strokeWidth=2.5;endArrow=classic;endFill=1;fontSize=9;fontColor=#8C4FFF;" edge="1" parent="1" source="alb" target="pod-gateway-a">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="925" y="800" />
          <mxPoint x="593" y="800" />
        </Array>
      </mxGeometry>
    </mxCell>
    <mxCell id="link-cdn-apigw" value="VWR API" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#E7157B;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;fontSize=9;fontColor=#E7157B;" edge="1" parent="1" source="cloudfront" target="api-gw-vwr">
      <mxGeometry relative="1" as="geometry">
        <mxPoint x="1043" y="363" as="targetPoint" />
        <Array as="points">
          <mxPoint x="1020" y="313" />
        </Array>
      </mxGeometry>
    </mxCell>
    <mxCell id="link-apigw-dynamo" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#2E73B8;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;fontSize=10;" edge="1" parent="1" source="api-gw-vwr" target="dynamodb">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="1750" y="464" />
          <mxPoint x="1750" y="1204" />
        </Array>
      </mxGeometry>
    </mxCell>
    <mxCell id="link-ticket-proxy" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#147EBA;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;" edge="1" parent="1" source="pod-ticket-a" target="rds-proxy">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="380" y="1380" />
          <mxPoint x="930" y="1380" />
        </Array>
      </mxGeometry>
    </mxCell>
    <mxCell id="link-ticket-redis" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#147EBA;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;" edge="1" parent="1" source="pod-ticket-a" target="redis-primary">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="400" y="1329" />
        </Array>
      </mxGeometry>
    </mxCell>
    <mxCell id="link-cdn-s3" value="Static Assets&#xa;(OAC)" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#8C4FFF;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;fontSize=10;fontColor=#8C4FFF;" edge="1" parent="1" source="cloudfront" target="s3">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="1560" y="280" />
          <mxPoint x="1560" y="954" />
        </Array>
      </mxGeometry>
    </mxCell>
    <mxCell id="link-vpce-s3" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#7AA116;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;" edge="1" parent="1" source="vpce-gw" target="s3">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="1000" y="1000" />
          <mxPoint x="1631" y="1000" />
        </Array>
      </mxGeometry>
    </mxCell>
    <mxCell id="link-sqs-worker" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#ED7100;strokeWidth=2;endArrow=classic;endFill=1;" edge="1" parent="1" source="sqs-a" target="ticket-worker-a">
      <mxGeometry relative="1" as="geometry" />
    </mxCell>
    <mxCell id="link-prom-amp" value="remote_write (SigV4)" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#E7157B;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;fontSize=8;fontColor=#E7157B;" edge="1" parent="1" target="ft0UTGreD4NaZBkYawH6-13">
      <mxGeometry relative="1" as="geometry">
        <mxPoint x="775" y="1055" as="sourcePoint" />
        <mxPoint x="1610" y="810" as="targetPoint" />
        <Array as="points">
          <mxPoint x="910" y="1055" />
          <mxPoint x="910" y="810" />
        </Array>
      </mxGeometry>
    </mxCell>
    <mxCell id="link-amp-amg" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#759C3E;strokeWidth=1.5;endArrow=classic;endFill=1;" edge="1" parent="1" source="ft0UTGreD4NaZBkYawH6-13" target="ft0UTGreD4NaZBkYawH6-14">
      <mxGeometry relative="1" as="geometry">
        <mxPoint x="1650" y="807" as="sourcePoint" />
        <mxPoint x="1690" y="807" as="targetPoint" />
      </mxGeometry>
    </mxCell>
    <mxCell id="link-argocd-eks" value="GitOps Deploy" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#EF7B4D;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;fontSize=9;fontColor=#EF7B4D;" edge="1" parent="1" source="argocd" target="eks-cp">
      <mxGeometry relative="1" as="geometry" />
    </mxCell>
    <mxCell id="link-ecr-eks" value="Image Pull" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#ED7100;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;fontSize=9;fontColor=#ED7100;entryX=1;entryY=0.5;entryDx=0;entryDy=0;" edge="1" parent="1" source="ecr" target="9XcHtQANKYaBjinsaZiJ-1">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="880" y="1120" />
          <mxPoint x="880" y="961" />
        </Array>
      </mxGeometry>
    </mxCell>
    <mxCell id="link-primary-standby" value="Sync Replication" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#C925D1;strokeWidth=1.5;endArrow=classic;endFill=1;dashed=1;fontSize=8;fontColor=#C925D1;" edge="1" parent="1" source="rds-primary" target="rds-standby">
      <mxGeometry relative="1" as="geometry" />
    </mxCell>
    <mxCell id="link-proxy-db" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#C925D1;strokeWidth=2;endArrow=classic;endFill=1;" edge="1" parent="1" source="rds-proxy" target="rds-primary">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="630" y="1430" />
        </Array>
        <mxPoint x="911" y="1473.5" as="sourcePoint" />
        <mxPoint x="588" y="1473.5" as="targetPoint" />
      </mxGeometry>
    </mxCell>
    <mxCell id="9XcHtQANKYaBjinsaZiJ-23" value="gateway-service" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#ED7100;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" vertex="1" parent="1">
      <mxGeometry x="1260" y="840" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="9XcHtQANKYaBjinsaZiJ-24" value="VPC Endpoint&#xa;(Interface x9)" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.endpoint;" vertex="1" parent="1">
      <mxGeometry x="1451" y="840" width="40" height="40" as="geometry" />
    </mxCell>
    <mxCell id="9XcHtQANKYaBjinsaZiJ-25" value="Monitoring Namespace" style="sketch=0;outlineConnect=0;fontColor=#666666;gradientColor=none;strokeColor=#666666;fillColor=none;dashed=1;verticalLabelPosition=top;verticalAlign=bottom;align=left;html=1;fontSize=10;fontStyle=1;pointerEvents=0;container=1;collapsible=0;recursiveResize=0;" vertex="1" parent="1">
      <mxGeometry x="1065" y="1015" width="460" height="72" as="geometry" />
    </mxCell>
    <mxCell id="9XcHtQANKYaBjinsaZiJ-26" value="Prometheus" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=8;fontStyle=0" vertex="1" parent="9XcHtQANKYaBjinsaZiJ-25">
      <mxGeometry x="10" y="9" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="9XcHtQANKYaBjinsaZiJ-27" value="Grafana" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=8;fontStyle=0" vertex="1" parent="9XcHtQANKYaBjinsaZiJ-25">
      <mxGeometry x="100" y="9" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="9XcHtQANKYaBjinsaZiJ-28" value="Loki" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=8;fontStyle=0" vertex="1" parent="9XcHtQANKYaBjinsaZiJ-25">
      <mxGeometry x="190" y="9" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="9XcHtQANKYaBjinsaZiJ-29" value="Zipkin" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=8;fontStyle=0" vertex="1" parent="9XcHtQANKYaBjinsaZiJ-25">
      <mxGeometry x="280" y="9" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="9XcHtQANKYaBjinsaZiJ-30" value="AlertManager" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=8;fontStyle=0" vertex="1" parent="9XcHtQANKYaBjinsaZiJ-25">
      <mxGeometry x="370" y="9" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="9XcHtQANKYaBjinsaZiJ-31" value="community&lt;div&gt;&lt;br&gt;&lt;/div&gt;" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" vertex="1" parent="1">
      <mxGeometry x="1441" y="924" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="9XcHtQANKYaBjinsaZiJ-32" value="EKS worker Nodes" style="sketch=0;outlineConnect=0;fontColor=#666666;gradientColor=none;strokeColor=#666666;fillColor=none;dashed=1;verticalLabelPosition=top;verticalAlign=bottom;align=left;html=1;fontSize=10;fontStyle=1;pointerEvents=0;container=1;collapsible=0;recursiveResize=0;" vertex="1" parent="1">
      <mxGeometry x="1065" y="924" width="436" height="72" as="geometry" />
    </mxCell>
    <mxCell id="9XcHtQANKYaBjinsaZiJ-33" value="auth" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" vertex="1" parent="9XcHtQANKYaBjinsaZiJ-32">
      <mxGeometry x="75" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="9XcHtQANKYaBjinsaZiJ-34" value="payment" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#C71313;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" vertex="1" parent="9XcHtQANKYaBjinsaZiJ-32">
      <mxGeometry x="135" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="9XcHtQANKYaBjinsaZiJ-35" value="stats" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" vertex="1" parent="9XcHtQANKYaBjinsaZiJ-32">
      <mxGeometry x="255" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="9XcHtQANKYaBjinsaZiJ-36" value="queue" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#C71313;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" vertex="1" parent="9XcHtQANKYaBjinsaZiJ-32">
      <mxGeometry x="195" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="9XcHtQANKYaBjinsaZiJ-37" value="catalog" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" vertex="1" parent="9XcHtQANKYaBjinsaZiJ-32">
      <mxGeometry x="315" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="9XcHtQANKYaBjinsaZiJ-38" value="ticket" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#C71313;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" vertex="1" parent="9XcHtQANKYaBjinsaZiJ-32">
      <mxGeometry x="9" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="pod-frontend-c" value="frontend" style="aspect=fixed;sketch=0;html=1;dashed=0;whitespace=wrap;verticalLabelPosition=bottom;verticalAlign=top;fillColor=#2875E2;strokeColor=#ffffff;shape=mxgraph.kubernetes.icon2;kubernetesLabel=1;prIcon=pod;fontSize=9;fontStyle=1" vertex="1" parent="9XcHtQANKYaBjinsaZiJ-32">
      <mxGeometry x="375" width="50" height="48" as="geometry" />
    </mxCell>
    <mxCell id="9XcHtQANKYaBjinsaZiJ-41" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#ED7100;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;exitX=0.46;exitY=-0.021;exitDx=0;exitDy=0;exitPerimeter=0;" edge="1" parent="1" source="9XcHtQANKYaBjinsaZiJ-39" target="eks-cp">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="414" y="840" />
          <mxPoint x="414" y="720" />
        </Array>
        <mxPoint x="414" y="846" as="sourcePoint" />
        <mxPoint x="1604" y="720" as="targetPoint" />
      </mxGeometry>
    </mxCell>
    <mxCell id="9XcHtQANKYaBjinsaZiJ-44" value="" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#8C4FFF;strokeWidth=3;endArrow=classic;endFill=1;fontSize=10;" edge="1" parent="1" source="edge-lambda" target="igw">
      <mxGeometry relative="1" as="geometry">
        <mxPoint x="890" y="465" as="sourcePoint" />
        <mxPoint x="930" y="621" as="targetPoint" />
      </mxGeometry>
    </mxCell>
    <mxCell id="igw" value="Internet Gateway" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#8C4FFF;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=11;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.internet_gateway;" vertex="1" parent="1">
      <mxGeometry x="895" y="509" width="60" height="60" as="geometry" />
    </mxCell>
    <mxCell id="9XcHtQANKYaBjinsaZiJ-48" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#8C4FFF;strokeWidth=3;endArrow=classic;endFill=1;fontSize=10;" edge="1" parent="1" source="igw" target="alb">
      <mxGeometry relative="1" as="geometry">
        <mxPoint x="1070" y="580" as="sourcePoint" />
        <mxPoint x="1070" y="704" as="targetPoint" />
      </mxGeometry>
    </mxCell>
    <mxCell id="edge-lambda" value="Lambda@Edge&#xa;Token Validation" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#ED7100;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.lambda_function;" vertex="1" parent="1">
      <mxGeometry x="907" y="439" width="35" height="35" as="geometry" />
    </mxCell>
    <mxCell id="ft0UTGreD4NaZBkYawH6-8" value="GitHub Actions&lt;br&gt;CI/CD Pipeline&lt;br&gt;━━━━━━━━&lt;br&gt;Build &amp;amp; Test&lt;br&gt;Push to ECR&lt;br&gt;Deploy to EKS&lt;br&gt;Rollback Support" style="shape=image;html=1;verticalAlign=top;verticalLabelPosition=bottom;labelBackgroundColor=#ffffff;imageAspect=0;aspect=fixed;image=https://cdn-icons-png.flaticon.com/512/25/25231.png;fontSize=9;fontStyle=1;direction=south;" vertex="1" parent="1">
      <mxGeometry x="1820" y="1101" width="60" height="60" as="geometry" />
    </mxCell>
    <mxCell id="ft0UTGreD4NaZBkYawH6-12" value="Image Pull" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#ED7100;strokeWidth=2;endArrow=classic;endFill=1;dashed=1;fontSize=9;fontColor=#ED7100;exitX=0.5;exitY=1;exitDx=0;exitDy=0;" edge="1" parent="1" source="ft0UTGreD4NaZBkYawH6-8" target="ecr">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="1760" y="1131" />
          <mxPoint x="1760" y="1130" />
        </Array>
        <mxPoint x="2552" y="1289" as="sourcePoint" />
        <mxPoint x="1730" y="1130" as="targetPoint" />
      </mxGeometry>
    </mxCell>
    <mxCell id="ft0UTGreD4NaZBkYawH6-13" value="&lt;span style=&quot;font-size: 9px; font-weight: 700;&quot;&gt;Amazon Managed&lt;/span&gt;&lt;br style=&quot;font-size: 9px; font-weight: 700;&quot;&gt;&lt;span style=&quot;font-size: 9px; font-weight: 700;&quot;&gt;Prometheus (AMP)&lt;/span&gt;" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#759C3E;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.cloudwatch;" vertex="1" parent="1">
      <mxGeometry x="1607" y="787" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="ft0UTGreD4NaZBkYawH6-14" value="&lt;span style=&quot;font-size: 9px; font-weight: 700;&quot;&gt;Amazon Managed&lt;/span&gt;&lt;br style=&quot;font-size: 9px; font-weight: 700;&quot;&gt;&lt;span style=&quot;font-size: 9px; font-weight: 700;&quot;&gt;Grafana (AMG)&lt;/span&gt;" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#759C3E;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=0;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.cloudwatch;" vertex="1" parent="1">
      <mxGeometry x="1694" y="787" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="rds-primary" value="RDS PostgreSQL&#xa;Primary (Multi-AZ)" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#C925D1;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.rds;movable=1;resizable=1;rotatable=1;deletable=1;editable=1;locked=0;connectable=1;" vertex="1" parent="1">
      <mxGeometry x="608" y="1467" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="ft0UTGreD4NaZBkYawH6-17" value="RDS PostgreSQL&lt;br&gt;Read Replica" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#C925D1;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=10;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.rds;movable=1;resizable=1;rotatable=1;deletable=1;editable=1;locked=0;connectable=1;" vertex="1" parent="1">
      <mxGeometry x="430" y="1467" width="48" height="48" as="geometry" />
    </mxCell>
    <mxCell id="link-primary-replica" value="Async Replication" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#C925D1;strokeWidth=1.5;endArrow=classic;endFill=1;dashed=1;fontSize=8;fontColor=#C925D1;movable=1;resizable=1;rotatable=1;deletable=1;editable=1;locked=0;connectable=1;" edge="1" parent="1" source="rds-primary" target="ft0UTGreD4NaZBkYawH6-17">
      <mxGeometry relative="1" as="geometry" />
    </mxCell>
    <mxCell id="counter-advancer" value="Counter Advancer&#xa;(EventBridge 1min)" style="sketch=0;outlineConnect=0;fontColor=#232F3E;gradientColor=none;fillColor=#ED7100;strokeColor=none;dashed=0;verticalLabelPosition=bottom;verticalAlign=top;align=center;html=1;fontSize=9;fontStyle=1;aspect=fixed;pointerEvents=1;shape=mxgraph.aws4.lambda_function;" vertex="1" parent="1">
      <mxGeometry x="1100" y="435" width="44" height="44" as="geometry" />
    </mxCell>
    <mxCell id="link-advancer-dynamo" value="batch 500" style="edgeStyle=orthogonalEdgeStyle;strokeColor=#2E73B8;strokeWidth=1.5;endArrow=classic;endFill=1;dashed=1;fontSize=8;fontColor=#2E73B8;" edge="1" parent="1" source="counter-advancer" target="dynamodb">
      <mxGeometry relative="1" as="geometry">
        <Array as="points">
          <mxPoint x="1770" y="457" />
          <mxPoint x="1770" y="1204" />
        </Array>
      </mxGeometry>
    </mxCell>
  </root>
</mxGraphModel>
