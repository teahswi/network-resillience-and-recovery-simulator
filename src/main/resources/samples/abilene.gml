# Abilene (Internet2) research backbone core, in Topology Zoo style GML.
graph [
  label "Abilene (Internet2)"
  directed 0
  node [
    id 0
    label "Seattle"
    Latitude 47.61
    Longitude -122.33
  ]
  node [
    id 1
    label "Sunnyvale"
    Latitude 37.37
    Longitude -122.04
  ]
  node [
    id 2
    label "Los Angeles"
    Latitude 34.05
    Longitude -118.24
  ]
  node [
    id 3
    label "Denver"
    Latitude 39.74
    Longitude -104.99
  ]
  node [
    id 4
    label "Kansas City"
    Latitude 39.1
    Longitude -94.58
  ]
  node [
    id 5
    label "Houston"
    Latitude 29.76
    Longitude -95.37
  ]
  node [
    id 6
    label "Chicago"
    Latitude 41.88
    Longitude -87.63
  ]
  node [
    id 7
    label "Indianapolis"
    Latitude 39.77
    Longitude -86.16
  ]
  node [
    id 8
    label "Atlanta"
    Latitude 33.75
    Longitude -84.39
  ]
  node [
    id 9
    label "Washington"
    Latitude 38.91
    Longitude -77.04
  ]
  node [
    id 10
    label "New York"
    Latitude 40.71
    Longitude -74.01
  ]
  edge [
    source 0
    target 1
    LinkSpeedRaw 10000000000
  ]
  edge [
    source 0
    target 3
    LinkSpeedRaw 10000000000
  ]
  edge [
    source 1
    target 2
    LinkSpeedRaw 10000000000
  ]
  edge [
    source 1
    target 3
    LinkSpeedRaw 10000000000
  ]
  edge [
    source 2
    target 5
    LinkSpeedRaw 10000000000
  ]
  edge [
    source 3
    target 4
    LinkSpeedRaw 10000000000
  ]
  edge [
    source 4
    target 5
    LinkSpeedRaw 10000000000
  ]
  edge [
    source 4
    target 7
    LinkSpeedRaw 10000000000
  ]
  edge [
    source 5
    target 8
    LinkSpeedRaw 10000000000
  ]
  edge [
    source 7
    target 6
    LinkSpeedRaw 10000000000
  ]
  edge [
    source 7
    target 8
    LinkSpeedRaw 10000000000
  ]
  edge [
    source 6
    target 10
    LinkSpeedRaw 10000000000
  ]
  edge [
    source 8
    target 9
    LinkSpeedRaw 10000000000
  ]
  edge [
    source 10
    target 9
    LinkSpeedRaw 10000000000
  ]
]
