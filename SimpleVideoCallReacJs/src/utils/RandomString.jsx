class RandomString {
    static randomString = RandomString.generateRandomString();

    // Method to generate a random 5-letter string
    static generateRandomString() {
        const characters = 'abcdefghijklmnopqrstuvwxyz1234567890';
        let result = '';
        for (let i = 0; i < 5; i++) {
            result += characters.charAt(Math.floor(Math.random() * characters.length));
        }
        return result;
    }
}

export default RandomString;
