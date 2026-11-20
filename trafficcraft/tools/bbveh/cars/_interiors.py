"""Model-specific interiors: steering wheel, instrument cluster, seats, colours and gear lever for every car.

wheel:   4spoke | 3spoke | 3spoke_flat | classic2 | classic3 | classic4    (wheel_r = rim radius, wheel_col = rim colour)
cluster: analog3 | analog2 | analog5 | tach_center | classic | digital | digital_wide   (gauge = face, ring, marks colours)
seat:    sport | bucket | flat
shifter: ball | tall | gated | auto | column | paddle
"""
BLACK = ('#0c0c0e', '#9a9ca2', '#e6e6e6')
CREAM = ('#e8e0c8', '#b8b2a0', '#222222')
WHITE = ('#f0f0ee', '#a0a4aa', '#111111')
ORANGE = ('#101012', '#c06020', '#ff9a40')
BLUE = ('#08101c', '#2a6ad0', '#8ec8ff')
RED = ('#0c0c0e', '#c01e28', '#ff5050')

INT = {
    'toyota_gr_supra_a90': dict(wheel='3spoke', wheel_r=0.18, wheel_col='#1a1a1c', cluster='digital', gauge=RED, seat='sport', leather='#1c1c1e',
                                fabric='#8a1a1e', dash='#18181a', shifter='auto'),
    'toyota_supra_mk4': dict(wheel='4spoke', wheel_r=0.185, wheel_col='#1e1e20', cluster='analog3', gauge=BLACK, seat='sport', leather='#1e1e20',
                             fabric='#2c2d30', dash='#232426', shifter='ball'),
    'nissan_gtr_r35': dict(wheel='3spoke', wheel_r=0.18, wheel_col='#1a1a1c', cluster='analog3', gauge=RED, seat='bucket', leather='#1b1b1d',
                           fabric='#3a1a1c', dash='#1c1c1e', shifter='paddle'),
    'bmw_m3_2026': dict(wheel='3spoke_flat', wheel_r=0.18, wheel_col='#1a1a1c', cluster='digital_wide', gauge=BLUE, seat='bucket', leather='#2a2a2c',
                        fabric='#c8541e', dash='#18191b', shifter='auto'),
    'ford_mustang_1964': dict(wheel='classic3', wheel_r=0.2, wheel_col='#e8e0cc', cluster='classic', gauge=CREAM, seat='flat', leather='#8a1c22',
                              fabric='#9a2a2e', dash='#6e1a1e', shifter='tall'),
    'chevrolet_camaro_1966': dict(wheel='classic3', wheel_r=0.2, wheel_col='#1a1a1c', cluster='classic', gauge=BLACK, seat='flat', leather='#1d1d1f',
                                  fabric='#2a2a2d', dash='#1b1b1d', shifter='auto'),
    'dodge_challenger_1969': dict(wheel='classic3', wheel_r=0.2, wheel_col='#2a2a2c', cluster='analog3', gauge=BLACK, seat='flat', leather='#1a1a1c',
                                  fabric='#303034', dash='#222224', shifter='tall'),
    'chevrolet_corvette_1953': dict(wheel='classic2', wheel_r=0.21, wheel_col='#e8e0cc', cluster='classic', gauge=CREAM, seat='flat', leather='#b5121b',
                                    fabric='#c0303a', dash='#b02028', shifter='auto'),
    'porsche_911_1963': dict(wheel='classic4', wheel_r=0.2, wheel_col='#5a3a20', cluster='analog5', gauge=BLACK, seat='flat', leather='#2a2422',
                             fabric='#4a4038', dash='#1e1c1a', shifter='tall'),
    'bmw_m3_1985': dict(wheel='3spoke', wheel_r=0.19, wheel_col='#1c1c1e', cluster='analog2', gauge=ORANGE, seat='sport', leather='#1c1c1e',
                        fabric='#2c2e33', dash='#1e1f21', shifter='ball'),
    'mercedes_sclass_1972': dict(wheel='classic4', wheel_r=0.21, wheel_col='#2a2420', cluster='classic', gauge=BLACK, seat='flat', leather='#8a6a46',
                                 fabric='#9a7a56', dash='#4a3020', shifter='auto'),
    'audi_rs6_2002': dict(wheel='3spoke', wheel_r=0.185, wheel_col='#1b1b1d', cluster='analog2', gauge=RED, seat='sport', leather='#1b1b1d',
                          fabric='#2b2b2e', dash='#202022', shifter='auto'),
    'honda_civic_1972': dict(wheel='classic2', wheel_r=0.2, wheel_col='#1a1a1a', cluster='classic', gauge=BLACK, seat='flat', leather='#2a2a2a',
                             fabric='#5a4a3a', dash='#222222', shifter='tall'),
    'honda_accord_1976': dict(wheel='classic4', wheel_r=0.2, wheel_col='#2a2622', cluster='analog2', gauge=BLACK, seat='flat', leather='#3a3026',
                              fabric='#6a5a46', dash='#2a2622', shifter='tall'),
    'toyota_corolla_1966': dict(wheel='classic2', wheel_r=0.21, wheel_col='#e0dccc', cluster='classic', gauge=CREAM, seat='flat', leather='#5a1a1e',
                                fabric='#7a2a2e', dash='#3a1a1c', shifter='column'),
    'toyota_camry_1982': dict(wheel='4spoke', wheel_r=0.19, wheel_col='#3a3a40', cluster='analog2', gauge=BLACK, seat='flat', leather='#3a3a40',
                              fabric='#4a4a52', dash='#2a2a30', shifter='auto'),
    'mazda_mx5_1989': dict(wheel='4spoke', wheel_r=0.18, wheel_col='#1d1d1f', cluster='analog2', gauge=WHITE, seat='sport', leather='#1d1d1f',
                           fabric='#2a2a2d', dash='#1f1f21', shifter='ball'),
    'subaru_wrx_sti_1992': dict(wheel='3spoke', wheel_r=0.18, wheel_col='#1c1c1e', cluster='analog2', gauge=WHITE, seat='bucket', leather='#1c1c1e',
                                fabric='#1d3f9a', dash='#1e1e20', shifter='ball'),
    'mitsubishi_lancer_evo_1992': dict(wheel='3spoke', wheel_r=0.18, wheel_col='#1c1c1e', cluster='analog2', gauge=BLACK, seat='bucket',
                                       leather='#1c1c1e', fabric='#3a3a3e', dash='#1e1e20', shifter='ball'),
    'volkswagen_golf_gti_1974': dict(wheel='4spoke', wheel_r=0.19, wheel_col='#1c1c1e', cluster='analog2', gauge=BLACK, seat='sport', leather='#1c1c1e',
                                     fabric='tartan', dash='#1e1e20', shifter='ball'),
    'jeep_wrangler_1986': dict(wheel='classic3', wheel_r=0.2, wheel_col='#1c1c1c', cluster='classic', gauge=BLACK, seat='flat', leather='#3a3226',
                               fabric='#4a4236', dash='#2a2620', shifter='tall'),
    'land_rover_range_rover_1970': dict(wheel='classic2', wheel_r=0.21, wheel_col='#1c1c1c', cluster='classic', gauge=BLACK, seat='flat',
                                        leather='#4a3a2a', fabric='#5a4a3a', dash='#1e1e1e', shifter='tall'),
    'tesla_model_s_2012': dict(wheel='3spoke', wheel_r=0.185, wheel_col='#1c1c1e', cluster='digital', gauge=BLUE, seat='sport', leather='#e8e4dc',
                               fabric='#dcd6cc', dash='#1c1c1e', shifter='column'),
    'hyundai_elantra_1990': dict(wheel='4spoke', wheel_r=0.19, wheel_col='#3a3a3e', cluster='analog2', gauge=BLACK, seat='flat', leather='#3a3a3e',
                                 fabric='#5a5a62', dash='#2a2a2e', shifter='auto'),
    'kia_stinger_2017': dict(wheel='3spoke_flat', wheel_r=0.18, wheel_col='#1c1c1e', cluster='digital', gauge=RED, seat='sport', leather='#6a1a1e',
                             fabric='#7a2a2e', dash='#1c1c1e', shifter='auto'),
    'ferrari_f40_1987': dict(wheel='3spoke', wheel_r=0.175, wheel_col='#1a1a1a', cluster='tach_center', gauge=ORANGE, seat='bucket', leather='#1a1a1a',
                             fabric='#b5121b', dash='#2a2a2a', shifter='gated'),
    'lamborghini_aventador_2011': dict(wheel='3spoke_flat', wheel_r=0.175, wheel_col='#1a1a1c', cluster='digital', gauge=('#0a0a0a', '#f2a900', '#ffd060'),
                                       seat='bucket', leather='#1a1a1c', fabric='#2c2c2e', dash='#141414', shifter='paddle'),
}
