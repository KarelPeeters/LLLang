var a: u8 = 1;
var primeCount = 0;

while (a <= 100) {
    a = a + 1;
    var b = 2;

    while (b * b <= a) {
        if (a % b == 0) break;
    }
}

print(primeCount);
print(false);