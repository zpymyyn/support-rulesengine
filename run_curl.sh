#!/bin/bash
for i in `seq 1 1000` 
do
echo $i
curl -X POST -H "Content-Type: application/json" -d ' {"device":"cupcountcamera1","readings":[{"name":"cupcount","value":"'$i'"}]}' http://localhost:48080/api/v1/event
done
